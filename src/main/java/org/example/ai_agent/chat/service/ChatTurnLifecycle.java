package org.example.ai_agent.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.chat.dto.ChatRequest;
import org.example.ai_agent.chat.entity.ChatTurnRow;
import org.example.ai_agent.chat.entity.Message;
import org.example.ai_agent.chat.repository.ChatTurnRepository;
import org.example.ai_agent.chat.repository.MessageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Deep ChatTurn Module for request-level idempotency and lifecycle transitions.
 * The Interface exposes a compact claim/result model; persistence ordering,
 * duplicate handling and state guards remain inside this Implementation.
 */
@Slf4j
@Component
public class ChatTurnLifecycle {

    static final String STALE_TURN_ERROR_CODE = "CHAT_STALE_TIMEOUT";

    private final ChatTurnRepository chatTurnRepository;
    private final MessageRepository messageRepository;
    private final AssistantConversationTurn conversationTurn;
    private final TransactionalOperator transactionalOperator;

    public ChatTurnLifecycle(ChatTurnRepository chatTurnRepository,
                             MessageRepository messageRepository,
                             AssistantConversationTurn conversationTurn,
                             TransactionalOperator transactionalOperator) {
        this.chatTurnRepository = chatTurnRepository;
        this.messageRepository = messageRepository;
        this.conversationTurn = conversationTurn;
        this.transactionalOperator = transactionalOperator;
    }

    /**
     * Atomically creates the durable turn and user message. A uniqueness
     * conflict is resolved by reading the already-claimed turn; it never
     * starts a second model invocation.
     */
    public Mono<Claim> claim(ChatRequest request) {
        String clientRequestId = ensureClientRequestId(request);
        return transactionalOperator.transactional(Mono.defer(() -> {
                    ChatTurnRow started = newStartedTurn(request, clientRequestId);
                    return chatTurnRepository.save(started)
                            .flatMap(savedTurn -> {
                                savedTurn.markPersisted();
                                return conversationTurn.ensureConversationExists(request)
                                        .then(Mono.defer(() -> chatTurnRepository.acquireConversation(
                                                        request.getConversationId(), savedTurn.getId())
                                                .onErrorMap(this::isDataIntegrityFailure,
                                                        error -> ChatStreamClientException.conversationBusy())))
                                        .then(Mono.defer(() -> conversationTurn.saveUserMessage(request)))
                                        .flatMap(savedUserMessage -> {
                                            savedTurn.setConversationId(request.getConversationId());
                                            savedTurn.setUserMessageId(savedUserMessage.getId());
                                            return chatTurnRepository.save(savedTurn)
                                                    .thenReturn(Claim.owned(savedTurn, savedUserMessage));
                                        });
                            });
                }))
                .onErrorResume(this::isDataIntegrityFailure,
                        error -> findExistingClaim(request, clientRequestId, error));
    }

    public Mono<Claim> markStreaming(Claim claim) {
        if (!claim.isOwned()) {
            return Mono.just(claim);
        }
        return chatTurnRepository.markStreaming(claim.turnId())
                .flatMap(updated -> updated == 1
                        ? Mono.just(claim.withStatus("STREAMING"))
                        : Mono.error(new IllegalStateException("ChatTurn 流式状态迁移被拒绝")));
    }

    /**
     * Persists the assistant message and completes the turn in one local MySQL
     * transaction. Retrying a transient failure re-subscribes the whole
     * transaction so a failed transaction is never reused.
     */
    public Mono<Message> completeWithAiMessage(Claim claim, ChatRequest request, String content) {
        if (!claim.isOwned()) {
            return Mono.error(new IllegalStateException("只有当前 ChatTurn 所有者可以完成请求"));
        }
        Mono<Message> completionTransaction = Mono.defer(() -> transactionalOperator.transactional(
                conversationTurn.saveAiMessageOnce(request, content)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("AI 回答不能为空")))
                        .flatMap(message -> persistRetrieval(claim, request).thenReturn(message))
                        .flatMap(assistantMessage -> completeTurn(claim, assistantMessage))
                        .flatMap(message -> chatTurnRepository.releaseConversation(claim.turnId()).thenReturn(message))));
        return conversationTurn.retryAiMessagePersistence(completionTransaction);
    }

    private Mono<Message> completeTurn(Claim claim, Message assistantMessage) {
        return chatTurnRepository.markCompleted(claim.turnId(), assistantMessage.getId())
                .flatMap(updated -> updated == 1
                        ? Mono.just(assistantMessage)
                        : confirmAlreadyCompleted(claim, assistantMessage));
    }

    private Mono<Void> persistRetrieval(Claim claim, ChatRequest request) {
        if (request.getRetrievalContext() == null) return Mono.empty();
        return Mono.defer(() -> chatTurnRepository.storeRetrieval(claim.turnId(),
                        ChatRetrievalSnapshots.encode(request.getRetrievalContext())))
                .flatMap(updated -> updated == 1 ? Mono.empty() : chatTurnRepository.findById(claim.turnId())
                        .filter(row -> "COMPLETED".equals(row.getStatus()))
                        .switchIfEmpty(Mono.error(new IllegalStateException("ChatTurn retrieval persistence rejected")))
                        .then());
    }

    private Mono<Message> confirmAlreadyCompleted(Claim claim, Message assistantMessage) {
        return chatTurnRepository.findById(claim.turnId())
                .filter(row -> "COMPLETED".equals(row.getStatus())
                        && Objects.equals(row.getAssistantMessageId(), assistantMessage.getId()))
                .map(ignored -> assistantMessage)
                .switchIfEmpty(Mono.error(new IllegalStateException("ChatTurn 完成状态迁移被拒绝")));
    }

    public Mono<Void> markFailed(Claim claim, Throwable error) {
        return transitionToFailure(claim, errorCode(error));
    }

    public Mono<Void> markEmptyResponse(Claim claim) {
        return transitionToFailure(claim, "CHAT_EMPTY_RESPONSE");
    }

    public Mono<Void> markCancelled(Claim claim) {
        if (!claim.isOwned()) {
            return Mono.empty();
        }
        return transactionalOperator.transactional(chatTurnRepository.markCancelled(claim.turnId())
                .flatMap(updated -> updated == 1
                        ? Mono.<Void>empty()
                        : confirmTerminalState(claim, "CANCELLED", "CHAT_CANCELLED",
                                "ChatTurn 取消状态迁移被拒绝"))
                .then(Mono.defer(() -> chatTurnRepository.releaseConversation(claim.turnId()))).then())
                .doOnError(error -> log.warn("写入 ChatTurn 取消状态失败: turnId={}, errorType={}",
                        claim.turnId(), error.getClass().getSimpleName()));
    }

    /**
     * Terminates active turns that can no longer have a live request owner.
     * The caller supplies an inactivity window that must exceed the longest
     * configured request execution time.
     */
    public Mono<Integer> recoverStaleTurns(Duration staleAfter, int batchSize) {
        if (staleAfter == null || staleAfter.isZero() || staleAfter.isNegative()) {
            return Mono.error(new IllegalArgumentException("ChatTurn 过期时间必须为正数"));
        }
        if (batchSize <= 0) {
            return Mono.error(new IllegalArgumentException("ChatTurn 恢复批量必须为正数"));
        }
        return transactionalOperator.transactional(chatTurnRepository.failStaleActiveTurns(
                        Math.max(1L, staleAfter.toSeconds()), batchSize, STALE_TURN_ERROR_CODE)
                .flatMap(count -> chatTurnRepository.releaseTerminalConversations().thenReturn(count)))
                .doOnNext(recovered -> {
                    if (recovered > 0) {
                        log.warn("已终止过期 ChatTurn: count={}, staleAfterSeconds={}",
                                recovered, staleAfter.toSeconds());
                    }
                });
    }

    private Mono<Void> transitionToFailure(Claim claim, String errorCode) {
        if (!claim.isOwned()) {
            return Mono.empty();
        }
        return transactionalOperator.transactional(chatTurnRepository.markFailed(claim.turnId(), errorCode)
                .flatMap(updated -> updated == 1
                        ? Mono.<Void>empty()
                        : confirmTerminalState(claim, "FAILED", errorCode,
                                "ChatTurn 失败状态迁移被拒绝"))
                .then(Mono.defer(() -> chatTurnRepository.releaseConversation(claim.turnId()))).then())
                .doOnError(error -> log.error("写入 ChatTurn 失败状态失败: turnId={}, errorType={}",
                        claim.turnId(), error.getClass().getSimpleName()));
    }

    private Mono<Void> confirmTerminalState(Claim claim, String status, String errorCode, String rejectionMessage) {
        return chatTurnRepository.findById(claim.turnId())
                .filter(row -> status.equals(row.getStatus()) && Objects.equals(errorCode, row.getErrorCode()))
                .switchIfEmpty(Mono.error(new IllegalStateException(rejectionMessage)))
                .then();
    }

    private Mono<Claim> findExistingClaim(ChatRequest request, String clientRequestId, Throwable originalError) {
        return chatTurnRepository.findByActorAndClientRequestId(
                        actorType(request), request.getUserId(), clientRequestId)
                .switchIfEmpty(Mono.error(originalError))
                .flatMap(row -> bindExistingConversation(request, row))
                .flatMap(this::toExistingClaim);
    }

    private Mono<ChatTurnRow> bindExistingConversation(ChatRequest request, ChatTurnRow row) {
        Long requestedConversationId = request.getConversationId();
        Long claimedConversationId = row.getConversationId();
        if (claimedConversationId == null) {
            return Mono.error(new IllegalStateException("ChatTurn 已提交但尚未绑定会话"));
        }
        if (requestedConversationId != null && !requestedConversationId.equals(claimedConversationId)) {
            return Mono.error(ChatStreamClientException.clientRequestConflict());
        }
        if (StringUtils.hasText(row.getRequestFingerprint())
                && !row.getRequestFingerprint().equals(requestFingerprint(request))) {
            return Mono.error(ChatStreamClientException.clientRequestConflict());
        }
        request.setConversationId(claimedConversationId);
        return Mono.just(row);
    }

    private Mono<Claim> toExistingClaim(ChatTurnRow row) {
        return switch (row.getStatus()) {
            case "COMPLETED" -> replayClaim(row);
            case "STARTED", "STREAMING" -> Mono.just(Claim.inProgress(row));
            default -> Mono.just(Claim.failed(row));
        };
    }

    private Mono<Claim> replayClaim(ChatTurnRow row) {
        if (row.getAssistantMessageId() == null) {
            return Mono.just(Claim.failed(row));
        }
        return messageRepository.findById(row.getAssistantMessageId())
                .map(message -> Claim.replay(row, message))
                .switchIfEmpty(Mono.just(Claim.failed(row)));
    }

    private ChatTurnRow newStartedTurn(ChatRequest request, String clientRequestId) {
        LocalDateTime now = LocalDateTime.now();
        ChatTurnRow turn = new ChatTurnRow();
        turn.setId(UUID.randomUUID().toString());
        turn.setActorType(actorType(request));
        turn.setActorId(request.getUserId());
        turn.setConversationId(request.getConversationId());
        turn.setClientRequestId(clientRequestId);
        turn.setRequestFingerprint(requestFingerprint(request));
        turn.setModelId(request.getEffectiveModelId());
        turn.setStatus("STARTED");
        turn.setAttempt(1);
        turn.setStartedAt(now);
        turn.setUpdatedAt(now);
        turn.markNew();
        return turn;
    }

    private String ensureClientRequestId(ChatRequest request) {
        if (StringUtils.hasText(request.getClientMessageId())) {
            String clientRequestId = request.getClientMessageId().trim();
            request.setClientMessageId(clientRequestId);
            return clientRequestId;
        }
        String clientRequestId = UUID.randomUUID().toString();
        request.setClientMessageId(clientRequestId);
        return clientRequestId;
    }

    private String actorType(ChatRequest request) {
        return StringUtils.hasText(request.getActorType()) ? request.getActorType() : "USER";
    }

    private String requestFingerprint(ChatRequest request) {
        List<Long> knowledgeBaseIds = request.getKnowledgeBaseIds() == null
                ? List.of()
                : request.getKnowledgeBaseIds().stream().sorted().toList();
        String canonicalRequest = canonicalPart(request.getTrimmedMessage())
                + canonicalPart(request.getEffectiveModelId())
                + canonicalPart(knowledgeBaseIds.toString())
                + canonicalPart(String.valueOf(request.getMaxRetrievedDocs()))
                + canonicalPart(String.valueOf(request.getMinSimilarity()))
                + canonicalPart(String.valueOf(request.getSaveMessage()))
                + canonicalPart(String.valueOf(request.getStreaming()))
                + canonicalPart(request.getTitle());
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String canonicalPart(String value) {
        String normalized = value == null ? "<null>" : value;
        return normalized.length() + ":" + normalized;
    }

    private String errorCode(Throwable error) {
        if (error instanceof ChatStreamClientException clientException) {
            return clientException.getErrorCode();
        }
        return "CHAT_ERROR";
    }

    private boolean isDataIntegrityFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof DataIntegrityViolationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public enum ClaimKind {
        OWNED,
        REPLAY,
        IN_PROGRESS,
        FAILED
    }

    public record Claim(ClaimKind kind, ChatTurnRow row, Message assistantMessage) {

        static Claim owned(ChatTurnRow row, Message userMessage) {
            return new Claim(ClaimKind.OWNED, row, null);
        }

        static Claim replay(ChatTurnRow row, Message assistantMessage) {
            return new Claim(ClaimKind.REPLAY, row, assistantMessage);
        }

        static Claim inProgress(ChatTurnRow row) {
            return new Claim(ClaimKind.IN_PROGRESS, row, null);
        }

        static Claim failed(ChatTurnRow row) {
            return new Claim(ClaimKind.FAILED, row, null);
        }

        Claim withStatus(String status) {
            row.setStatus(status);
            return this;
        }

        public String turnId() {
            return row.getId();
        }

        public boolean isOwned() {
            return kind == ClaimKind.OWNED;
        }
    }
}
