package org.example.ai_agent.service.impl.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.entity.ChatTurnRow;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.observability.ChatStreamTelemetry;
import org.example.ai_agent.service.ConversationService;
import org.example.ai_agent.service.RagService;
import org.example.ai_agent.service.assistant.GeneralAssistant;
import org.example.ai_agent.service.assistant.GeneralAssistantFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("high-risk")
class AiChatServiceImplTest {

    @Mock private GeneralAssistantFactory generalAssistantFactory;
    @Mock private ConversationService conversationService;
    @Mock private RagService ragService;
    @Mock private GeneralAssistant generalAssistant;
    @Mock private ChatTurnLifecycle chatTurnLifecycle;

    private AiChatServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new AiChatServiceImpl(
                generalAssistantFactory,
                new AssistantConversationTurn(conversationService),
                chatTurnLifecycle,
                new AssistantInputBuilder(ragService),
                new ChatStreamEventFactory(objectMapper),
                new ChatStreamTelemetry(new SimpleMeterRegistry(), objectMapper),
                1000L);
    }

    @Test
    void chat_shouldStreamChineseChunksAndCompleteTurnOnlyAfterAiMessageIsSaved() {
        ChatRequest request = request(99L, "request-1");
        Conversation conversation = conversation(99L);
        ChatTurnLifecycle.Claim claim = ownedClaim("turn-1");
        Message savedAiMessage = new Message();
        savedAiMessage.setId(2L);
        savedAiMessage.setSender("ai");

        when(conversationService.getConversationById(99L, 42L)).thenReturn(Mono.just(conversation));
        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.just(claim));
        when(conversationService.addMessageToConversation(argThat(message ->
                message != null && "ai".equals(message.getSender())))).thenReturn(Mono.just(savedAiMessage));
        when(chatTurnLifecycle.markCompleted(claim, savedAiMessage)).thenReturn(Mono.empty());
        when(generalAssistantFactory.createAssistant(any(ChatRequest.class))).thenReturn(generalAssistant);
        when(generalAssistant.chat("99:test-model", "早上好")).thenReturn(Flux.just("早上", "好"));

        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> {
                    org.junit.jupiter.api.Assertions.assertEquals(5, events.size());
                    org.junit.jupiter.api.Assertions.assertEquals("metadata", events.get(0).event());
                    org.junit.jupiter.api.Assertions.assertEquals("status", events.get(1).event());
                    org.junit.jupiter.api.Assertions.assertEquals("status", events.get(2).event());
                    org.junit.jupiter.api.Assertions.assertEquals("message", events.get(3).event());
                    org.junit.jupiter.api.Assertions.assertEquals("早上", events.get(3).data());
                    org.junit.jupiter.api.Assertions.assertEquals("好", events.get(4).data());
                })
                .verifyComplete();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(conversationService, timeout(1000)).addMessageToConversation(captor.capture());
        Message persistedAiMessage = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("早上好", persistedAiMessage.getContent());
        org.junit.jupiter.api.Assertions.assertEquals("ai:42:99:request-1", persistedAiMessage.getDeduplicationKey());
        verify(chatTurnLifecycle).markCompleted(claim, savedAiMessage);
    }

    @Test
    void chat_shouldReplayCompletedTurnWithoutCreatingAnotherModelStream() {
        ChatRequest request = request(99L, "request-1");
        Conversation conversation = conversation(99L);
        ChatTurnRow row = startedTurn("turn-1");
        row.setStatus("COMPLETED");
        Message savedAiMessage = new Message();
        savedAiMessage.setContent("已保存的回答");
        ChatTurnLifecycle.Claim replay = ChatTurnLifecycle.Claim.replay(row, savedAiMessage);

        when(conversationService.getConversationById(99L, 42L)).thenReturn(Mono.just(conversation));
        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(replay));

        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> {
                    org.junit.jupiter.api.Assertions.assertEquals(
                            List.of("metadata", "status", "message"),
                            events.stream().map(ServerSentEvent::event).toList());
                    org.junit.jupiter.api.Assertions.assertEquals("已保存的回答", events.get(2).data());
                })
                .verifyComplete();

        verify(generalAssistantFactory, org.mockito.Mockito.never()).createAssistant(any());
    }

    @Test
    void chat_shouldEmitErrorForInProgressDuplicateWithoutCreatingAnotherModelStream() {
        ChatRequest request = request(99L, "request-1");
        ChatTurnLifecycle.Claim inProgress = ChatTurnLifecycle.Claim.inProgress(startedTurn("turn-1"));
        when(conversationService.getConversationById(99L, 42L)).thenReturn(Mono.just(conversation(99L)));
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
                new AssistantConversationTurn(conversationService),
                chatTurnLifecycle,
                new AssistantInputBuilder(ragService),
                new ChatStreamEventFactory(new ObjectMapper()),
                new ChatStreamTelemetry(new SimpleMeterRegistry(), new ObjectMapper()),
                10L);
        ChatRequest request = request(100L, "request-timeout");
        ChatTurnLifecycle.Claim claim = ownedClaim("turn-timeout");

        when(conversationService.getConversationById(100L, 42L)).thenReturn(Mono.just(conversation(100L)));
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

        when(conversationService.getConversationById(99L, 42L)).thenReturn(Mono.just(conversation(99L)));
        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.just(claim));
        when(conversationService.addMessageToConversation(any(Message.class))).thenReturn(Mono.error(persistenceFailure));
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
        verify(chatTurnLifecycle, org.mockito.Mockito.never()).markCompleted(eq(claim), any());
    }

    @Test
    void chat_shouldMarkTurnCancelledWhenClientCancelsTheStream() {
        ChatRequest request = request(99L, "request-cancel");
        ChatTurnLifecycle.Claim claim = ownedClaim("turn-cancel");

        when(conversationService.getConversationById(99L, 42L)).thenReturn(Mono.just(conversation(99L)));
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
    void chat_shouldKeepEmptyAnswerSseBehaviorButMarkTurnFailed() {
        ChatRequest request = request(99L, "request-empty");
        ChatTurnLifecycle.Claim claim = ownedClaim("turn-empty");

        when(conversationService.getConversationById(99L, 42L)).thenReturn(Mono.just(conversation(99L)));
        when(chatTurnLifecycle.claim(request)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markStreaming(claim)).thenReturn(Mono.just(claim));
        when(chatTurnLifecycle.markEmptyResponse(claim)).thenReturn(Mono.empty());
        when(generalAssistantFactory.createAssistant(any(ChatRequest.class))).thenReturn(generalAssistant);
        when(generalAssistant.chat("99:test-model", "早上好")).thenReturn(Flux.empty());

        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> org.junit.jupiter.api.Assertions.assertEquals(
                        List.of("metadata", "status", "status"),
                        events.stream().map(ServerSentEvent::event).toList()))
                .verifyComplete();

        verify(chatTurnLifecycle).markEmptyResponse(claim);
        verify(chatTurnLifecycle, org.mockito.Mockito.never()).markCompleted(eq(claim), any());
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

    private Conversation conversation(Long id) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        return conversation;
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
}
