package org.example.ai_agent.service.impl.chat;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.entity.ChatTurnRow;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.repository.chat.ChatTurnRepository;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Deep ChatTurn Module for request-level idempotency and lifecycle transitions.
 * The Interface exposes a compact claim/result model; persistence ordering,
 * duplicate handling and state guards remain inside this Implementation.
 */
@Slf4j
@Component
public class ChatTurnLifecycle {

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
                                return conversationTurn.saveUserMessage(request)
                                    .flatMap(savedUserMessage -> {
                                        savedTurn.setUserMessageId(savedUserMessage.getId());
                                        return chatTurnRepository.save(savedTurn)
                                                .thenReturn(Claim.owned(savedTurn, savedUserMessage));
                                    });
                            });
                }))
                .onErrorResume(this::isDuplicateClaim,
                        ignored -> findExistingClaim(request, clientRequestId));
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

    public Mono<Void> markCompleted(Claim claim, Message assistantMessage) {
        if (!claim.isOwned()) {
            return Mono.empty();
        }
        return chatTurnRepository.markCompleted(claim.turnId(), assistantMessage.getId())
                .flatMap(updated -> updated == 1
                        ? Mono.<Void>empty()
                        : Mono.error(new IllegalStateException("ChatTurn 完成状态迁移被拒绝")));
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
        return chatTurnRepository.markCancelled(claim.turnId())
                .doOnError(error -> log.warn("写入 ChatTurn 取消状态失败: turnId={}, errorType={}",
                        claim.turnId(), error.getClass().getSimpleName()))
                .onErrorResume(ignored -> Mono.empty())
                .then();
    }

    private Mono<Void> transitionToFailure(Claim claim, String errorCode) {
        if (!claim.isOwned()) {
            return Mono.empty();
        }
        return chatTurnRepository.markFailed(claim.turnId(), errorCode)
                .doOnError(error -> log.error("写入 ChatTurn 失败状态失败: turnId={}, errorType={}",
                        claim.turnId(), error.getClass().getSimpleName()))
                .onErrorResume(ignored -> Mono.empty())
                .then();
    }

    private Mono<Claim> findExistingClaim(ChatRequest request, String clientRequestId) {
        return chatTurnRepository.findByActorAndConversationAndClientRequestId(
                        actorType(request), request.getUserId(), request.getConversationId(), clientRequestId)
                .switchIfEmpty(Mono.error(new IllegalStateException("ChatTurn 唯一键冲突但未找到已有记录")))
                .flatMap(this::toExistingClaim);
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

    private String errorCode(Throwable error) {
        if (error instanceof ChatStreamClientException clientException) {
            return clientException.getErrorCode();
        }
        return "CHAT_ERROR";
    }

    private boolean isDuplicateClaim(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof DataIntegrityViolationException) {
                String message = current.getMessage();
                return message != null && (message.contains("uk_chat_turn_actor_conversation_request")
                        || message.contains("Duplicate entry"));
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
