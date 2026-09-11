package org.example.ai_agent.knowledge.ingestion;

import java.time.LocalDateTime;

public record CleanupOperation(String operationId, String status, int step, int attempt,
                               String errorCode, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
