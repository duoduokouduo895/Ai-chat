package org.example.ai_agent.chat.service;

import org.example.ai_agent.chat.dto.ChatRequest;
import org.example.ai_agent.chat.entity.ChatTurnRow;
import org.example.ai_agent.chat.entity.Conversation;
import org.example.ai_agent.chat.entity.Message;
import org.example.ai_agent.chat.repository.ChatTurnRepository;
import org.example.ai_agent.chat.repository.MessageRepository;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
        lenient().when(chatTurnRepository.acquireConversation(any(), any())).thenReturn(Mono.just(1));
        lenient().when(chatTurnRepository.releaseConversation(any())).thenReturn(Mono.just(1));
        lenient().when(chatTurnRepository.releaseTerminalConversations()).thenReturn(Mono.just(1));
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lifecycle = new ChatTurnLifecycle(chatTurnRepository, messageRepository,
                new AssistantConversationTurn(conversationService), transactionalOperator);
    }

    @Test
    void retrievalPersistenceFailurePreventsCompletionAndSlotRelease() {
        var claim = ChatTurnLifecycle.Claim.owned(turn("turn-rag", "STREAMING"), new Message());
        var request = request(99L, "rag-request");
        request.setRetrievalContext(new org.example.ai_agent.knowledge.service.RagService.RetrievalContext(
                "question", List.of(), "", 0, 1L));
        var saved = new Message(); saved.setId(202L);
        when(conversationService.addMessageToConversation(any())).thenReturn(Mono.just(saved));
        when(chatTurnRepository.storeRetrieval(any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("retrieval persistence failed")));
        StepVerifier.create(lifecycle.completeWithAiMessage(claim, request, "answer"))
                .expectError(IllegalStateException.class).verify();
        verify(chatTurnRepository, never()).markCompleted(any(), any());
        verify(chatTurnRepository, never()).releaseConversation(any());
    }

    @Test
    void snapshotsPersistReferencesWithoutQuestionOrEvidenceBody() throws Exception {
        var match = new org.example.ai_agent.knowledge.dto.KnowledgeSearchResultDto.KnowledgeSearchMatch();
        match.setKnowledgeBaseId(1L); match.setDocumentId(2L); match.setVersionId(3L); match.setChunkId(4L);
        match.setContent("private evidence body"); match.setDocumentTitle("private title");
        var context = new org.example.ai_agent.knowledge.service.RagService.RetrievalContext("private question", List.of(match),
                "private prompt", 1, 1L);
        String json = ChatRetrievalSnapshots.encode(context);
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("private"));
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        org.junit.jupiter.api.Assertions.assertEquals(3L, tree.path("sources").get(0).path("versionId").asLong());
    }

    @Test
    void claim_shouldCreateTurnAndUserMessageWithOneStableClientRequestId() {
        ChatRequest request = request(99L, null);
        Message savedUserMessage = new Message();
        savedUserMessage.setId(101L);
        List<Boolean> entityNewStates = new ArrayList<>();

        when(chatTurnRepository.save(any(ChatTurnRow.class)))
                .thenAnswer(invocation -> {
                    ChatTurnRow row = invocation.getArgument(0);
                    entityNewStates.add(row.isNew());
                    return Mono.just(row);
                });
        when(conversationService.getConversationById(99L, 42L)).thenReturn(Mono.just(conversation(99L)));
        when(conversationService.addMessageToConversation(any(Message.class))).thenReturn(Mono.just(savedUserMessage));

        StepVerifier.create(lifecycle.claim(request))
                .assertNext(claim -> {
                    org.junit.jupiter.api.Assertions.assertEquals(ChatTurnLifecycle.ClaimKind.OWNED, claim.kind());
                    org.junit.jupiter.api.Assertions.assertEquals("USER", claim.row().getActorType());
                    org.junit.jupiter.api.Assertions.assertEquals(42L, claim.row().getActorId());
                    org.junit.jupiter.api.Assertions.assertEquals(99L, claim.row().getConversationId());
                    org.junit.jupiter.api.Assertions.assertNotNull(request.getClientMessageId());
                    org.junit.jupiter.api.Assertions.assertEquals(request.getClientMessageId(), claim.row().getClientRequestId());
                    org.junit.jupiter.api.Assertions.assertNotNull(claim.row().getRequestFingerprint());
                    org.junit.jupiter.api.Assertions.assertEquals(64, claim.row().getRequestFingerprint().length());
                    org.junit.jupiter.api.Assertions.assertEquals(101L, claim.row().getUserMessageId());
                    org.junit.jupiter.api.Assertions.assertEquals("STARTED", claim.row().getStatus());
                    org.junit.jupiter.api.Assertions.assertFalse(claim.row().isNew());
                })
                .verifyComplete();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(conversationService).addMessageToConversation(messageCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                "user:42:99:" + request.getClientMessageId(), messageCaptor.getValue().getDeduplicationKey());
        verify(chatTurnRepository, org.mockito.Mockito.times(2)).save(any(ChatTurnRow.class));
        org.junit.jupiter.api.Assertions.assertEquals(List.of(true, false), entityNewStates);
    }

    @Test
    void claim_shouldClaimRequestBeforeCreatingANewConversation() {
        ChatRequest request = request(null, "new-conversation-request");
        Message savedUserMessage = new Message();
        savedUserMessage.setId(101L);
        List<Long> conversationIdsAtSave = new ArrayList<>();

        when(chatTurnRepository.save(any(ChatTurnRow.class)))
                .thenAnswer(invocation -> {
                    ChatTurnRow row = invocation.getArgument(0);
                    conversationIdsAtSave.add(row.getConversationId());
                    return Mono.just(row);
                });
        when(conversationService.createConversation(any(Conversation.class)))
                .thenReturn(Mono.just(conversation(123L)));
        when(conversationService.addMessageToConversation(any(Message.class))).thenReturn(Mono.just(savedUserMessage));

        StepVerifier.create(lifecycle.claim(request))
                .assertNext(claim -> {
                    org.junit.jupiter.api.Assertions.assertEquals(ChatTurnLifecycle.ClaimKind.OWNED, claim.kind());
                    org.junit.jupiter.api.Assertions.assertEquals(123L, request.getConversationId());
                    org.junit.jupiter.api.Assertions.assertEquals(123L, claim.row().getConversationId());
                    org.junit.jupiter.api.Assertions.assertEquals(101L, claim.row().getUserMessageId());
                })
                .verifyComplete();

        verify(chatTurnRepository, org.mockito.Mockito.times(2)).save(any(ChatTurnRow.class));
        org.junit.jupiter.api.Assertions.assertNull(conversationIdsAtSave.getFirst());
        org.junit.jupiter.api.Assertions.assertEquals(123L, conversationIdsAtSave.getLast());
    }

    @Test
    void claim_shouldReturnInProgressForConcurrentUniqueKeyConflictWithoutSavingSecondUserMessage() {
        ChatRequest request = request(null, "same-request");
        ChatTurnRow existing = turn("existing", "STREAMING");

        when(chatTurnRepository.save(any(ChatTurnRow.class)))
                .thenReturn(Mono.error(new DataIntegrityViolationException("Duplicate entry for uk_chat_turn_actor_request")));
        when(chatTurnRepository.findByActorAndClientRequestId("USER", 42L, "same-request"))
                .thenReturn(Mono.just(existing));

        StepVerifier.create(lifecycle.claim(request))
                .assertNext(claim -> org.junit.jupiter.api.Assertions.assertEquals(
                        ChatTurnLifecycle.ClaimKind.IN_PROGRESS, claim.kind()))
                .verifyComplete();

        org.junit.jupiter.api.Assertions.assertEquals(99L, request.getConversationId());
        verify(conversationService, never()).createConversation(any());
        verify(conversationService, never()).addMessageToConversation(any());
    }

    @Test
    void claim_shouldReturnReplayForCompletedTurnWithPersistedAssistantMessage() {
        ChatRequest request = request(null, "same-request");
        ChatTurnRow existing = turn("existing", "COMPLETED");
        existing.setAssistantMessageId(202L);
        Message assistantMessage = new Message();
        assistantMessage.setId(202L);
        assistantMessage.setContent("保存的回答");

        when(chatTurnRepository.save(any(ChatTurnRow.class)))
                .thenReturn(Mono.error(new DataIntegrityViolationException("Duplicate entry for uk_chat_turn_actor_request")));
        when(chatTurnRepository.findByActorAndClientRequestId("USER", 42L, "same-request"))
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
    void claim_shouldRejectClientRequestIdReusedForAnotherConversation() {
        ChatRequest request = request(100L, "same-request");
        ChatTurnRow existing = turn("existing", "COMPLETED");

        when(chatTurnRepository.save(any(ChatTurnRow.class)))
                .thenReturn(Mono.error(new DataIntegrityViolationException("Duplicate entry for uk_chat_turn_actor_request")));
        when(chatTurnRepository.findByActorAndClientRequestId("USER", 42L, "same-request"))
                .thenReturn(Mono.just(existing));

        StepVerifier.create(lifecycle.claim(request))
                .expectErrorSatisfies(error -> {
                    org.junit.jupiter.api.Assertions.assertInstanceOf(ChatStreamClientException.class, error);
                    org.junit.jupiter.api.Assertions.assertEquals(
                            "CHAT_REQUEST_CONFLICT", ((ChatStreamClientException) error).getErrorCode());
                })
                .verify();

        verify(messageRepository, never()).findById(any(Long.class));
    }

    @Test
    void completeWithAiMessage_shouldPersistMessageAndTransitionInsideOneTransaction() {
        ChatTurnLifecycle.Claim claim = ChatTurnLifecycle.Claim.owned(turn("turn-1", "STREAMING"), new Message());
        ChatRequest request = request(99L, "same-request");
        Message assistantMessage = new Message();
        assistantMessage.setId(202L);
        when(conversationService.addMessageToConversation(any(Message.class))).thenReturn(Mono.just(assistantMessage));
        when(chatTurnRepository.markCompleted("turn-1", 202L)).thenReturn(Mono.just(1));

        StepVerifier.create(lifecycle.completeWithAiMessage(claim, request, " 已保存回答 "))
                .expectNext(assistantMessage)
                .verifyComplete();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(conversationService).addMessageToConversation(messageCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("ai", messageCaptor.getValue().getSender());
        org.junit.jupiter.api.Assertions.assertEquals("已保存回答", messageCaptor.getValue().getContent());
        org.junit.jupiter.api.Assertions.assertEquals(
                "ai:42:99:same-request", messageCaptor.getValue().getDeduplicationKey());
        verify(chatTurnRepository).markCompleted("turn-1", 202L);
        verify(transactionalOperator).transactional(any(Mono.class));
    }

    @Test
    void completeWithAiMessage_shouldFailWhenCompletionTransitionIsRejected() {
        ChatTurnLifecycle.Claim claim = ChatTurnLifecycle.Claim.owned(turn("turn-1", "STREAMING"), new Message());
        ChatRequest request = request(99L, "same-request");
        Message assistantMessage = new Message();
        assistantMessage.setId(202L);
        when(conversationService.addMessageToConversation(any(Message.class))).thenReturn(Mono.just(assistantMessage));
        when(chatTurnRepository.markCompleted("turn-1", 202L)).thenReturn(Mono.just(0));
        when(chatTurnRepository.findById("turn-1")).thenReturn(Mono.just(turn("turn-1", "STREAMING")));

        StepVerifier.create(lifecycle.completeWithAiMessage(claim, request, "回答"))
                .expectErrorSatisfies(error -> org.junit.jupiter.api.Assertions.assertEquals(
                        "ChatTurn 完成状态迁移被拒绝", error.getMessage()))
                .verify();
    }

    @Test
    void completeWithAiMessage_shouldAcceptAlreadyCommittedMatchingTerminalState() {
        ChatTurnLifecycle.Claim claim = ChatTurnLifecycle.Claim.owned(turn("turn-1", "STREAMING"), new Message());
        ChatRequest request = request(99L, "same-request");
        Message assistantMessage = new Message();
        assistantMessage.setId(202L);
        ChatTurnRow completed = turn("turn-1", "COMPLETED");
        completed.setAssistantMessageId(202L);
        when(conversationService.addMessageToConversation(any(Message.class))).thenReturn(Mono.just(assistantMessage));
        when(chatTurnRepository.markCompleted("turn-1", 202L)).thenReturn(Mono.just(0));
        when(chatTurnRepository.findById("turn-1")).thenReturn(Mono.just(completed));

        StepVerifier.create(lifecycle.completeWithAiMessage(claim, request, "回答"))
                .expectNext(assistantMessage)
                .verifyComplete();
    }

    @Test
    void markFailed_shouldPropagateRejectedTerminalTransition() {
        ChatTurnLifecycle.Claim claim = ChatTurnLifecycle.Claim.owned(turn("turn-1", "STREAMING"), new Message());
        when(chatTurnRepository.markFailed("turn-1", "CHAT_ERROR")).thenReturn(Mono.just(0));
        when(chatTurnRepository.findById("turn-1")).thenReturn(Mono.just(turn("turn-1", "COMPLETED")));

        StepVerifier.create(lifecycle.markFailed(claim, new RuntimeException("model failed")))
                .expectErrorSatisfies(error -> org.junit.jupiter.api.Assertions.assertEquals(
                        "ChatTurn 失败状态迁移被拒绝", error.getMessage()))
                .verify();
    }

    @Test
    void markFailed_shouldAcceptAlreadyCommittedMatchingTerminalState() {
        ChatTurnLifecycle.Claim claim = ChatTurnLifecycle.Claim.owned(turn("turn-1", "STREAMING"), new Message());
        ChatTurnRow failed = turn("turn-1", "FAILED");
        failed.setErrorCode("CHAT_ERROR");
        when(chatTurnRepository.markFailed("turn-1", "CHAT_ERROR")).thenReturn(Mono.just(0));
        when(chatTurnRepository.findById("turn-1")).thenReturn(Mono.just(failed));

        StepVerifier.create(lifecycle.markFailed(claim, new RuntimeException("model failed")))
                .verifyComplete();
    }

    @Test
    void recoverStaleTurns_shouldTerminateOnlyRowsOlderThanTheRequestedWindow() {
        when(chatTurnRepository.failStaleActiveTurns(
                900L, 200, ChatTurnLifecycle.STALE_TURN_ERROR_CODE)).thenReturn(Mono.just(3));

        StepVerifier.create(lifecycle.recoverStaleTurns(Duration.ofMinutes(15), 200))
                .expectNext(3)
                .verifyComplete();

        verify(chatTurnRepository).failStaleActiveTurns(
                900L, 200, ChatTurnLifecycle.STALE_TURN_ERROR_CODE);
    }

    @Test
    void recoverStaleTurns_shouldRejectNonPositiveWindow() {
        StepVerifier.create(lifecycle.recoverStaleTurns(Duration.ZERO, 200))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(chatTurnRepository, never()).failStaleActiveTurns(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    private ChatRequest request(Long conversationId, String clientMessageId) {
        return ChatRequest.builder()
                .userId(42L)
                .conversationId(conversationId)
                .prompt("你好")
                .model("test-model")
                .clientMessageId(clientMessageId)
                .actorType("USER")
                .build();
    }

    private Conversation conversation(Long id) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setUserId(42L);
        return conversation;
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
