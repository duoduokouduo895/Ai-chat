package org.example.ai_agent.chat.service;

import org.example.ai_agent.chat.dto.ChatRequest;
import org.example.ai_agent.chat.entity.Conversation;
import org.example.ai_agent.chat.entity.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AssistantConversationTurnTest {

    @Mock
    private ConversationService conversationService;

    @Test
    void ensureConversationExists_shouldCreateNewConversation() {
        AssistantConversationTurn turn = new AssistantConversationTurn(conversationService);
        ChatRequest request = ChatRequest.builder()
                .userId(42L)
                .prompt("你好")
                .model("test-model")
                .build();
        Conversation saved = new Conversation();
        saved.setId(99L);

        when(conversationService.createConversation(any(Conversation.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(turn.ensureConversationExists(request))
                .expectNext(request)
                .verifyComplete();

        org.junit.jupiter.api.Assertions.assertEquals(99L, request.getConversationId());
        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationService).createConversation(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(42L, captor.getValue().getUserId());
        org.junit.jupiter.api.Assertions.assertEquals("New Conversation", captor.getValue().getTitle());
    }

    @Test
    void ensureConversationExists_shouldValidateExistingConversation() {
        AssistantConversationTurn turn = new AssistantConversationTurn(conversationService);
        ChatRequest request = ChatRequest.builder()
                .userId(42L)
                .conversationId(99L)
                .prompt("你好")
                .model("test-model")
                .build();
        Conversation conversation = new Conversation();
        conversation.setId(99L);

        when(conversationService.getConversationById(99L, 42L)).thenReturn(Mono.just(conversation));

        StepVerifier.create(turn.ensureConversationExists(request))
                .expectNext(request)
                .verifyComplete();

        verify(conversationService, never()).createConversation(any());
    }

    @Test
    void saveMessages_shouldUseStableDeduplicationKeys() {
        AssistantConversationTurn turn = new AssistantConversationTurn(conversationService);
        ChatRequest request = ChatRequest.builder()
                .userId(42L)
                .conversationId(99L)
                .prompt("  你好  ")
                .model("test-model")
                .clientMessageId("request-1")
                .build();

        when(conversationService.addMessageToConversation(any(Message.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(turn.saveUserMessage(request))
                .assertNext(message -> {
                    org.junit.jupiter.api.Assertions.assertEquals("user", message.getSender());
                    org.junit.jupiter.api.Assertions.assertEquals("你好", message.getContent());
                    org.junit.jupiter.api.Assertions.assertEquals("user:42:99:request-1", message.getDeduplicationKey());
                })
                .verifyComplete();

        StepVerifier.create(turn.saveAiMessage(request, " 回答 "))
                .assertNext(message -> {
                    org.junit.jupiter.api.Assertions.assertEquals("ai", message.getSender());
                    org.junit.jupiter.api.Assertions.assertEquals("回答", message.getContent());
                    org.junit.jupiter.api.Assertions.assertEquals("ai:42:99:request-1", message.getDeduplicationKey());
                })
                .verifyComplete();
    }

    @Test
    void saveAiMessage_shouldSkipEmptyContent() {
        AssistantConversationTurn turn = new AssistantConversationTurn(conversationService);
        ChatRequest request = ChatRequest.builder()
                .userId(42L)
                .conversationId(99L)
                .prompt("你好")
                .model("test-model")
                .build();

        StepVerifier.create(turn.saveAiMessage(request, "   "))
                .verifyComplete();

        verify(conversationService, never()).addMessageToConversation(any());
    }

    @Test
    void saveAiMessage_shouldPropagateNonTransientPersistenceFailure() {
        AssistantConversationTurn turn = new AssistantConversationTurn(conversationService);
        ChatRequest request = ChatRequest.builder()
                .userId(42L)
                .conversationId(99L)
                .prompt("你好")
                .model("test-model")
                .clientMessageId("request-1")
                .build();
        RuntimeException failure = new RuntimeException("database unavailable");
        when(conversationService.addMessageToConversation(any(Message.class)))
                .thenReturn(Mono.error(failure));

        StepVerifier.create(turn.saveAiMessage(request, "回答"))
                .expectErrorSatisfies(error -> org.junit.jupiter.api.Assertions.assertSame(failure, error))
                .verify();

        verify(conversationService, times(1)).addMessageToConversation(any(Message.class));
    }
}
