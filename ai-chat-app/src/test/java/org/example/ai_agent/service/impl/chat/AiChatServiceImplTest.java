package org.example.ai_agent.service.impl.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.entity.ChatTurnRow;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.observability.ChatStreamTelemetry;
import org.example.ai_agent.service.RagService;
import org.example.ai_agent.service.assistant.GeneralAssistant;
import org.example.ai_agent.service.assistant.GeneralAssistantFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("high-risk")
class AiChatServiceImplTest {

    @Mock private GeneralAssistantFactory generalAssistantFactory;
    @Mock private RagService ragService;
    @Mock private GeneralAssistant generalAssistant;
    @Mock private ChatTurnLifecycle chatTurnLifecycle;

    private AiChatServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new AiChatServiceImpl(
                generalAssistantFactory,
                chatTurnLifecycle,
                new AssistantInputBuilder(ragService, contextLoader(), queryRewriter()),
                new ChatStreamEventFactory(objectMapper),
                new ChatStreamTelemetry(new SimpleMeterRegistry(), objectMapper),
                1000L, retrievalReader());
    }

    @Test
    void emptyEvidenceCompletesWithFixedReplyWithoutCallingModel() {
        var request = request(99L, "empty-evidence");
        request.setKnowledgeBaseIds(List.of(7L));
        var claim = ownedClaim("empty-turn");
        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.just(claim));
        when(ragService.retrieveContext(any(), any(), any(), any(), any(), eq(false)))
                .thenReturn(Mono.just(new RagService.RetrievalContext("问题", List.of(), "", 0, 1L)));
        when(chatTurnLifecycle.completeWithAiMessage(claim, request, RagService.INSUFFICIENT_EVIDENCE))
                .thenReturn(Mono.just(new Message()));
        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> org.junit.jupiter.api.Assertions.assertEquals(
                        RagService.INSUFFICIENT_EVIDENCE, events.get(events.size() - 2).data()))
                .verifyComplete();
        verify(generalAssistantFactory, org.mockito.Mockito.never()).createAssistant(any());
    }

    @Test
    void unavailableRetrievalFailsWithoutCallingModel() {
        var request = request(99L, "unavailable-evidence");
        request.setKnowledgeBaseIds(List.of(7L));
        var claim = ownedClaim("unavailable-turn");
        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markFailed(eq(claim), any())).thenReturn(Mono.empty());
        when(ragService.retrieveContext(any(), any(), any(), any(), any(), eq(false)))
                .thenReturn(Mono.just(new RagService.RetrievalContext("问题", List.of(), "", 0, 1L,
                        org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.Status.UNAVAILABLE, List.of())));
        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> org.junit.jupiter.api.Assertions.assertTrue(
                        events.getLast().data().contains("CHAT_RETRIEVAL_UNAVAILABLE")))
                .verifyComplete();
        verify(generalAssistantFactory, org.mockito.Mockito.never()).createAssistant(any());
        verify(chatTurnLifecycle, org.mockito.Mockito.never()).completeWithAiMessage(any(), any(), any());
    }

    @Test
    void wholeTurnDeadlineStopsContinuousOutput() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "maxTurnDurationMs", 200L);
        ChatRequest request = request(99L, "deadline");
        var claim = ownedClaim("turn-deadline");
        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markFailed(eq(claim), any())).thenReturn(Mono.empty());
        when(generalAssistantFactory.createAssistant(any())).thenReturn(generalAssistant);
        when(generalAssistant.chat("99:test-model", "早上好"))
                .thenReturn(Flux.interval(java.time.Duration.ofMillis(10)).map(i -> "持续输出"));
        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> org.junit.jupiter.api.Assertions.assertEquals("error", events.getLast().event()))
                .expectComplete().verify(java.time.Duration.ofSeconds(5));
        verify(chatTurnLifecycle).markFailed(eq(claim), any());
        verify(chatTurnLifecycle, org.mockito.Mockito.never()).completeWithAiMessage(any(), any(), any());
    }

    @Test
    void chat_shouldStreamChineseChunksAndCompleteTurnOnlyAfterAiMessageIsSaved() {
        ChatRequest request = request(99L, "request-1");
        ChatTurnLifecycle.Claim claim = ownedClaim("turn-1");
        Message savedAiMessage = new Message();
        savedAiMessage.setId(2L);
        savedAiMessage.setSender("ai");

        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.completeWithAiMessage(claim, request, "早上好"))
                .thenReturn(Mono.just(savedAiMessage));
        when(generalAssistantFactory.createAssistant(any(ChatRequest.class))).thenReturn(generalAssistant);
        when(generalAssistant.chat("99:test-model", "早上好")).thenReturn(Flux.just("早上", "好"));

        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> {
                    org.junit.jupiter.api.Assertions.assertEquals(6, events.size());
                    org.junit.jupiter.api.Assertions.assertEquals("metadata", events.get(0).event());
                    org.junit.jupiter.api.Assertions.assertEquals("status", events.get(1).event());
                    org.junit.jupiter.api.Assertions.assertEquals("status", events.get(2).event());
                    org.junit.jupiter.api.Assertions.assertEquals("message", events.get(3).event());
                    org.junit.jupiter.api.Assertions.assertEquals("早上", events.get(3).data());
                    org.junit.jupiter.api.Assertions.assertEquals("好", events.get(4).data());
                })
                .verifyComplete();

        verify(chatTurnLifecycle).completeWithAiMessage(claim, request, "早上好");
    }

    @Test
    void chat_shouldReplayCompletedTurnWithoutCreatingAnotherModelStream() {
        ChatRequest request = request(99L, "request-1");
        ChatTurnRow row = startedTurn("turn-1");
        row.setStatus("COMPLETED");
        Message savedAiMessage = new Message();
        savedAiMessage.setContent("已保存的回答");
        ChatTurnLifecycle.Claim replay = ChatTurnLifecycle.Claim.replay(row, savedAiMessage);

        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(replay));

        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> {
                    org.junit.jupiter.api.Assertions.assertEquals(
                            List.of("metadata", "status", "message", "complete"),
                            events.stream().map(ServerSentEvent::event).toList());
                    org.junit.jupiter.api.Assertions.assertEquals("已保存的回答", events.get(2).data());
                    org.junit.jupiter.api.Assertions.assertEquals("[DONE]", events.get(3).data());
                    org.junit.jupiter.api.Assertions.assertEquals("turn-1", events.get(3).id());
                })
                .verifyComplete();

        verify(generalAssistantFactory, org.mockito.Mockito.never()).createAssistant(any());
    }

    @Test
    void chat_shouldEmitErrorForInProgressDuplicateWithoutCreatingAnotherModelStream() {
        ChatRequest request = request(99L, "request-1");
        ChatTurnLifecycle.Claim inProgress = ChatTurnLifecycle.Claim.inProgress(startedTurn("turn-1"));
        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(inProgress));

        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> {
                    org.junit.jupiter.api.Assertions.assertEquals(1, events.size());
                    org.junit.jupiter.api.Assertions.assertEquals("error", events.getFirst().event());
                    org.junit.jupiter.api.Assertions.assertTrue(events.getFirst().data().contains("CHAT_REQUEST_IN_PROGRESS"));
                })
                .verifyComplete();

        verify(generalAssistantFactory, org.mockito.Mockito.never()).createAssistant(any());
    }

    @Test
    void chat_shouldEmitErrorAndMarkTurnFailedWhenModelStreamTimesOut() {
        AiChatServiceImpl timeoutService = new AiChatServiceImpl(
                generalAssistantFactory,
                chatTurnLifecycle,
                new AssistantInputBuilder(ragService, contextLoader(), queryRewriter()),
                new ChatStreamEventFactory(new ObjectMapper()),
                new ChatStreamTelemetry(new SimpleMeterRegistry(), new ObjectMapper()),
                10L, retrievalReader());
        ChatRequest request = request(100L, "request-timeout");
        ChatTurnLifecycle.Claim claim = ownedClaim("turn-timeout");

        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markFailed(eq(claim), any())).thenReturn(Mono.empty());
        when(generalAssistantFactory.createAssistant(any(ChatRequest.class))).thenReturn(generalAssistant);
        when(generalAssistant.chat("100:test-model", "早上好")).thenReturn(Flux.never());

        StepVerifier.create(timeoutService.chat(request).collectList())
                .assertNext(events -> {
                    org.junit.jupiter.api.Assertions.assertEquals(
                            List.of("metadata", "status", "status", "error"),
                            events.stream().map(ServerSentEvent::event).toList());
                    org.junit.jupiter.api.Assertions.assertTrue(
                            events.get(3).data().contains("模型响应超时，请稍后重试或切换模型。"));
                })
                .verifyComplete();

        verify(chatTurnLifecycle).markFailed(eq(claim), any());
    }

    @Test
    void chat_shouldMarkTurnFailedWhenAiMessagePersistenceFails() {
        ChatRequest request = request(99L, "request-save-failure");
        ChatTurnLifecycle.Claim claim = ownedClaim("turn-save-failure");
        RuntimeException persistenceFailure = new RuntimeException("database unavailable");

        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.completeWithAiMessage(claim, request, "回答"))
                .thenReturn(Mono.error(persistenceFailure));
        when(chatTurnLifecycle.markFailed(eq(claim), any())).thenReturn(Mono.empty());
        when(generalAssistantFactory.createAssistant(any(ChatRequest.class))).thenReturn(generalAssistant);
        when(generalAssistant.chat("99:test-model", "早上好")).thenReturn(Flux.just("回答"));

        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> {
                    org.junit.jupiter.api.Assertions.assertEquals(
                            List.of("metadata", "status", "status", "message", "error"),
                            events.stream().map(ServerSentEvent::event).toList());
                })
                .verifyComplete();

        verify(chatTurnLifecycle).markFailed(eq(claim), any());
        verify(chatTurnLifecycle).completeWithAiMessage(claim, request, "回答");
    }

    @Test
    void chat_shouldMarkTurnFailedWhenStreamingTransitionFails() {
        ChatRequest request = request(99L, "request-streaming-failure");
        ChatTurnLifecycle.Claim claim = ownedClaim("turn-streaming-failure");
        RuntimeException transitionFailure = new RuntimeException("streaming transition failed");

        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.error(transitionFailure));
        when(chatTurnLifecycle.markFailed(claim, transitionFailure)).thenReturn(Mono.empty());

        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> org.junit.jupiter.api.Assertions.assertEquals(
                        List.of("error"), events.stream().map(ServerSentEvent::event).toList()))
                .verifyComplete();

        verify(chatTurnLifecycle).markFailed(claim, transitionFailure);
        verify(generalAssistantFactory, org.mockito.Mockito.never()).createAssistant(any());
    }

    @Test
    void chat_shouldMarkTurnFailedWhenRagInputConstructionFails() {
        ChatRequest request = request(99L, "request-rag-failure");
        request.setKnowledgeBaseIds(List.of(7L));
        ChatTurnLifecycle.Claim claim = ownedClaim("turn-rag-failure");
        RuntimeException retrievalFailure = new RuntimeException("retrieval unavailable");

        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.just(claim));
        when(ragService.retrieveContext("早上好", List.of(7L), 5, 0.3, 42L, false))
                .thenReturn(Mono.error(retrievalFailure));
        when(chatTurnLifecycle.markFailed(claim, retrievalFailure)).thenReturn(Mono.empty());

        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> org.junit.jupiter.api.Assertions.assertEquals(
                        List.of("metadata", "status", "error"),
                        events.stream().map(ServerSentEvent::event).toList()))
                .verifyComplete();

        verify(chatTurnLifecycle).markFailed(claim, retrievalFailure);
        verify(generalAssistantFactory, org.mockito.Mockito.never()).createAssistant(any());
    }

    @Test
    void chat_shouldKeepOriginalSseErrorWhenFailedTerminalizationAlsoFails() {
        ChatRequest request = request(99L, "request-terminalization-failure");
        ChatTurnLifecycle.Claim claim = ownedClaim("turn-terminalization-failure");
        RuntimeException modelFailure = new RuntimeException("model unavailable");
        RuntimeException terminalizationFailure = new RuntimeException("database unavailable");

        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.just(claim));
        when(generalAssistantFactory.createAssistant(any(ChatRequest.class))).thenReturn(generalAssistant);
        when(generalAssistant.chat("99:test-model", "早上好")).thenReturn(Flux.error(modelFailure));
        when(chatTurnLifecycle.markFailed(claim, modelFailure)).thenReturn(Mono.error(terminalizationFailure));

        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> {
                    org.junit.jupiter.api.Assertions.assertEquals(
                            List.of("metadata", "status", "status", "error"),
                            events.stream().map(ServerSentEvent::event).toList());
                    org.junit.jupiter.api.Assertions.assertEquals(1, modelFailure.getSuppressed().length);
                    org.junit.jupiter.api.Assertions.assertSame(
                            terminalizationFailure, modelFailure.getSuppressed()[0]);
                })
                .verifyComplete();
    }

    @Test
    void chat_shouldMarkTurnCancelledWhenClientCancelsTheStream() {
        ChatRequest request = request(99L, "request-cancel");
        ChatTurnLifecycle.Claim claim = ownedClaim("turn-cancel");

        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markCancelled(claim)).thenReturn(Mono.empty());

        StepVerifier.create(service.chat(request))
                .expectNextCount(3)
                .thenCancel()
                .verify();

        verify(chatTurnLifecycle, timeout(1000)).markCancelled(claim);
        verify(chatTurnLifecycle, org.mockito.Mockito.never()).markFailed(eq(claim), any());
    }

    @Test
    void chat_shouldReportEmptyAnswerAndMarkTurnFailed() {
        ChatRequest request = request(99L, "request-empty");
        ChatTurnLifecycle.Claim claim = ownedClaim("turn-empty");

        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markEmptyResponse(claim)).thenReturn(Mono.empty());
        when(generalAssistantFactory.createAssistant(any(ChatRequest.class))).thenReturn(generalAssistant);
        when(generalAssistant.chat("99:test-model", "早上好")).thenReturn(Flux.empty());

        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> org.junit.jupiter.api.Assertions.assertEquals(
                        List.of("metadata", "status", "status", "error"),
                        events.stream().map(ServerSentEvent::event).toList()))
                .verifyComplete();

        verify(chatTurnLifecycle).markEmptyResponse(claim);
        verify(chatTurnLifecycle, org.mockito.Mockito.never()).completeWithAiMessage(eq(claim), any(), any());
    }

    private ChatRetrievalReader retrievalReader() {
        var reader = org.mockito.Mockito.mock(ChatRetrievalReader.class);
        org.mockito.Mockito.lenient().when(reader.read(org.mockito.ArgumentMatchers.nullable(String.class), any(), any(boolean.class)))
                .thenReturn(Mono.empty());
        return reader;
    }

    private ChatRequest request(Long conversationId, String clientMessageId) {
        return ChatRequest.builder()
                .userId(42L)
                .conversationId(conversationId)
                .prompt("早上好")
                .model("test-model")
                .clientMessageId(clientMessageId)
                .actorType("USER")
                .build();
    }

    private ChatTurnLifecycle.Claim ownedClaim(String id) {
        return ChatTurnLifecycle.Claim.owned(startedTurn(id), new Message());
    }

    private ChatTurnRow startedTurn(String id) {
        ChatTurnRow turn = new ChatTurnRow();
        turn.setId(id);
        turn.setStatus("STARTED");
        return turn;
    }
    private ConversationContextLoader contextLoader() {
        ConversationContextLoader loader = org.mockito.Mockito.mock(ConversationContextLoader.class);
        org.mockito.Mockito.lenient().when(loader.prepare(any(), any()))
                .thenAnswer(call -> Mono.just(call.getArgument(1)));
        org.mockito.Mockito.lenient().when(loader.load(any())).thenReturn(Mono.just(List.of()));
        org.mockito.Mockito.lenient().when(loader.prepare(any(), any(), any()))
                .thenAnswer(call -> Mono.just(call.getArgument(1)));
        return loader;
    }

    private org.example.ai_agent.service.assistant.RetrievalQueryRewriter queryRewriter() {
        var rewriter = org.mockito.Mockito.mock(org.example.ai_agent.service.assistant.RetrievalQueryRewriter.class);
        org.mockito.Mockito.lenient().when(rewriter.rewrite(any(), any()))
                .thenAnswer(call -> Mono.just(((ChatRequest) call.getArgument(0)).getTrimmedMessage()));
        return rewriter;
    }

}
