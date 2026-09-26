package org.example.ai_agent.service.assistant;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.FinishReason;
import org.example.ai_agent.config.ai.ModelCatalogProperties;
import org.example.ai_agent.config.ai.RetryingStreamingChatModel;
import org.example.ai_agent.conversation.CompletedContextTurn;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetrievalQueryRewriterTest {
    private final AiModelRegistry registry = mock(AiModelRegistry.class);
    private final RetrievalQueryRewriter rewriter = new RetrievalQueryRewriter(registry, new ModelCatalogProperties());
    private final org.example.ai_agent.dto.chat.ChatRequest request = org.example.ai_agent.dto.chat.ChatRequest.builder()
            .conversationId(8L).model("chat-fast").prompt("它支持多少并发连接？").build();

    @Test
    void firstTurnDoesNotCallModel() {
        StepVerifier.create(rewriter.rewrite(request, List.of()))
                .expectNext(request.getPrompt()).verifyComplete();
        verifyNoInteractions(registry);
    }

    @Test
    void rewritesUsingOnlyThreeRecentWholeTurnsWithoutTools() {
        var captured = new AtomicReference<ChatRequest>();
        when(registry.getStreamingModel("chat-fast")).thenReturn(new StreamingChatModel() {
            @Override
            public void chat(ChatRequest input, StreamingChatResponseHandler handler) {
                captured.set(input);
                handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("A 产品支持多少并发连接？"))
                        .finishReason(FinishReason.STOP).build());
            }
        });
        StepVerifier.create(rewriter.rewrite(request, List.of(turn("old"), turn("middle"), turn("recent"), turn("A 产品"))))
                .expectNext("A 产品支持多少并发连接？").verifyComplete();

        var input = captured.get();
        assertEquals(8, input.messages().size());
        assertFalse(input.messages().contains(UserMessage.from("old")));
        assertEquals(UserMessage.from("middle"), input.messages().get(1));
        assertEquals(UserMessage.from(request.getPrompt()), input.messages().getLast());
        assertEquals(256, input.maxOutputTokens());
        assertTrue(input.toolSpecifications().isEmpty());
        assertTrue(new AssistantInputBudget(4096, null).estimateInput(input.messages(), List.of()) <= 4096);
        assertEquals("它支持多少并发连接？", request.getPrompt());
    }

    @Test
    void oversizedHistoryFallsBackWithoutCallingModel() {
        StepVerifier.create(rewriter.rewrite(request, List.of(turn("字".repeat(4000)))))
                .expectNext(request.getPrompt()).verifyComplete();
        verifyNoInteractions(registry);
    }

    @Test
    void providerFailureFallsBackToOriginalQuestion() {
        var model = mock(StreamingChatModel.class);
        when(registry.getStreamingModel("chat-fast")).thenReturn(model);
        doAnswer(call -> {
            ((StreamingChatResponseHandler) call.getArgument(1)).onError(new IllegalStateException("provider failed"));
            return null;
        }).when(model).chat(any(ChatRequest.class), any());
        StepVerifier.create(rewriter.rewrite(request, List.of(turn("A 产品"))))
                .expectNext(request.getPrompt()).verifyComplete();
    }

    @Test
    void blankMultilineOversizedAndTruncatedRewritesFallBack() {
        for (var response : List.of(
                ChatResponse.builder().aiMessage(AiMessage.from(" ")).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("解释\n查询")).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("字".repeat(513))).build(),
                ChatResponse.builder().aiMessage(AiMessage.from("不完整的查询")).finishReason(FinishReason.LENGTH).build())) {
            when(registry.getStreamingModel("chat-fast")).thenReturn(new StreamingChatModel() {
                @Override
                public void chat(ChatRequest input, StreamingChatResponseHandler handler) {
                    handler.onCompleteResponse(response);
                }
            });
            StepVerifier.create(rewriter.rewrite(request, List.of(turn("A 产品"))))
                    .expectNext(request.getPrompt()).verifyComplete();
        }
    }

    @Test
    void timeoutFallsBackAndCancelsStreamThroughRetryWrapper() {
        var handle = mock(StreamingHandle.class);
        var model = mock(StreamingChatModel.class);
        doAnswer(call -> {
            ((StreamingChatResponseHandler) call.getArgument(1)).onPartialResponse(
                    mock(PartialResponse.class), new PartialResponseContext(handle));
            return null;
        }).when(model).chat(any(ChatRequest.class), any());
        when(registry.getStreamingModel("chat-fast"))
                .thenReturn(new RetryingStreamingChatModel(model, "chat-fast", 2, 0, Runnable::run));

        StepVerifier.create(rewriter.rewrite(request, List.of(turn("A 产品"))))
                .expectNext(request.getPrompt()).expectComplete().verify(Duration.ofSeconds(7));
        verify(handle).cancel();
        verify(model, times(1)).chat(any(ChatRequest.class), any());
    }

    @Test
    void callerCancellationCancelsRewriteWithoutEmittingFallback() {
        var handle = mock(StreamingHandle.class);
        var started = new CountDownLatch(1);
        var model = mock(StreamingChatModel.class);
        doAnswer(call -> {
            ((StreamingChatResponseHandler) call.getArgument(1)).onPartialResponse(
                    mock(PartialResponse.class), new PartialResponseContext(handle));
            started.countDown();
            return null;
        }).when(model).chat(any(ChatRequest.class), any());
        when(registry.getStreamingModel("chat-fast")).thenReturn(model);

        StepVerifier.create(rewriter.rewrite(request, List.of(turn("A 产品"))))
                .then(() -> {
                    try {
                        assertTrue(started.await(2, TimeUnit.SECONDS));
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        fail(error);
                    }
                }).thenCancel().verify(Duration.ofSeconds(3));
        verify(handle).cancel();
    }

    private CompletedContextTurn turn(String text) {
        return new CompletedContextTurn(text, text, "介绍 " + text, LocalDateTime.now());
    }
}
