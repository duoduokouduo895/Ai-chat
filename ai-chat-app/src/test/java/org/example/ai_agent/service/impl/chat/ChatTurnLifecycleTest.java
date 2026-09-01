package org.example.ai_agent.service.impl.chat;

import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.entity.ChatTurnRow;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.repository.chat.ChatTurnRepository;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.example.ai_agent.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatTurnLifecycleTest {

    @Mock private ChatTurnRepository chatTurnRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private ConversationService conversationService;
    @Mock private TransactionalOperator transactionalOperator;

    private ChatTurnLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lifecycle = new ChatTurnLifecycle(chatTurnRepository, messageRepository,
                new AssistantConversationTurn(conversationService), transactionalOperator);
    }

    @Test
    void claim_shouldCreateTurnAndUserMessageWithOneStableClientRequestId() {
        ChatRequest request = request(null);
        Message savedUserMessage = new Message();
        savedUserMessage.setId(101L);

        when(chatTurnRepository.save(any(ChatTurnRow.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(conversationService.addMessageToConversation(any(Message.class))).thenReturn(Mono.just(savedUserMessage));

        StepVerifier.create(lifecycle.claim(request))
                .assertNext(claim -> {
                    org.junit.jupiter.api.Assertions.assertEquals(ChatTurnLifecycle.ClaimKind.OWNED, claim.kind());
                    org.junit.jupiter.api.Assertions.assertEquals("USER", claim.row().getActorType());
                    org.junit.jupiter.api.Assertions.assertEquals(42L, claim.row().getActorId());
                    org.junit.jupiter.api.Assertions.assertEquals(99L, claim.row().getConversationId());
                    org.junit.jupiter.api.Assertions.assertNotNull(request.getClientMessageId());
                    org.junit.jupiter.api.Assertions.assertEquals(request.getClientMessageId(), claim.row().getClientRequestId());
                    org.junit.jupiter.api.Assertions.assertEquals(101L, claim.row().getUserMessageId());
                    org.junit.jupiter.api.Assertions.assertEquals("STARTED", claim.row().getStatus());
                })
                .verifyComplete();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(conversationService).addMessageToConversation(messageCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                "user:42:99:" + request.getClientMessageId(), messageCaptor.getValue().getDeduplicationKey());
        verify(chatTurnRepository, org.mockito.Mockito.times(2)).save(any(ChatTurnRow.class));
    }

    @Test
    void claim_shouldReturnInProgressForConcurrentUniqueKeyConflictWithoutSavingSecondUserMessage() {
        ChatRequest request = request("same-request");
        ChatTurnRow existing = turn("existing", "STREAMING");

        when(chatTurnRepository.save(any(ChatTurnRow.class)))
                .thenReturn(Mono.error(new DataIntegrityViolationException("Duplicate entry for uk_chat_turn_actor_conversation_request")));
        when(chatTurnRepository.findByActorAndConversationAndClientRequestId("USER", 42L, 99L, "same-request"))
                .thenReturn(Mono.just(existing));

        StepVerifier.create(lifecycle.claim(request))
                .assertNext(claim -> org.junit.jupiter.api.Assertions.assertEquals(
                        ChatTurnLifecycle.ClaimKind.IN_PROGRESS, claim.kind()))
                .verifyComplete();

        verify(conversationService, never()).addMessageToConversation(any());
    }

    @Test
    void claim_shouldReturnReplayForCompletedTurnWithPersistedAssistantMessage() {
        ChatRequest request = request("same-request");
        ChatTurnRow existing = turn("existing", "COMPLETED");
        existing.setAssistantMessageId(202L);
        Message assistantMessage = new Message();
        assistantMessage.setId(202L);
        assistantMessage.setContent("保存的回答");

        when(chatTurnRepository.save(any(ChatTurnRow.class)))
                .thenReturn(Mono.error(new DataIntegrityViolationException("Duplicate entry for uk_chat_turn_actor_conversation_request")));
        when(chatTurnRepository.findByActorAndConversationAndClientRequestId("USER", 42L, 99L, "same-request"))
                .thenReturn(Mono.just(existing));
        when(messageRepository.findById(202L)).thenReturn(Mono.just(assistantMessage));

        StepVerifier.create(lifecycle.claim(request))
                .assertNext(claim -> {
                    org.junit.jupiter.api.Assertions.assertEquals(ChatTurnLifecycle.ClaimKind.REPLAY, claim.kind());
                    org.junit.jupiter.api.Assertions.assertEquals("保存的回答", claim.assistantMessage().getContent());
                })
                .verifyComplete();

        verify(conversationService, never()).addMessageToConversation(any());
    }

    @Test
    void markCompleted_shouldRequireAssistantMessagePersistenceBeforeTransition() {
        ChatTurnLifecycle.Claim claim = ChatTurnLifecycle.Claim.owned(turn("turn-1", "STREAMING"), new Message());
        Message assistantMessage = new Message();
        assistantMessage.setId(202L);
        when(chatTurnRepository.markCompleted("turn-1", 202L)).thenReturn(Mono.just(1));

        StepVerifier.create(lifecycle.markCompleted(claim, assistantMessage))
                .verifyComplete();

        verify(chatTurnRepository).markCompleted("turn-1", 202L);
    }

    private ChatRequest request(String clientMessageId) {
        return ChatRequest.builder()
                .userId(42L)
                .conversationId(99L)
                .prompt("你好")
                .model("test-model")
                .clientMessageId(clientMessageId)
                .actorType("USER")
                .build();
    }

    private ChatTurnRow turn(String id, String status) {
        ChatTurnRow row = new ChatTurnRow();
        row.setId(id);
        row.setStatus(status);
        row.setActorType("USER");
        row.setActorId(42L);
        row.setConversationId(99L);
        row.setClientRequestId("same-request");
        return row;
    }
}
