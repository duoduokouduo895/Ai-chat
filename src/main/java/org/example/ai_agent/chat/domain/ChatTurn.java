package org.example.ai_agent.chat.domain;

import org.example.ai_agent.common.domain.RequestActor;

import java.time.Instant;
import java.util.Objects;

/**
 * Framework-free state for a single idempotent chat request.
 * Persistence and SSE encoding remain adapters around this model.
 */
public record ChatTurn(String id,
                       RequestActor actor,
                       long conversationId,
                       String clientRequestId,
                       String modelId,
                       ChatTurnStatus status,
                       int attempt,
                       Instant startedAt,
                       Instant updatedAt) {

    public ChatTurn {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actor, "actor");
        if (conversationId <= 0) {
            throw new IllegalArgumentException("conversationId must be positive");
        }
        if (clientRequestId == null || clientRequestId.isBlank()) {
            throw new IllegalArgumentException("clientRequestId must not be blank");
        }
        Objects.requireNonNull(status, "status");
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must not be negative");
        }
    }
}
