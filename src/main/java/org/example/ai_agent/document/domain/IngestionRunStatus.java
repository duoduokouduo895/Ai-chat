package org.example.ai_agent.document.domain;

import java.util.Locale;

public enum IngestionRunStatus {
    PENDING,
    PROCESSING,
    RETRY,
    COMPLETED,
    FAILED,
    CANCELLED;

    public static IngestionRunStatus fromValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Ingestion run status is required");
        }
        try {
            return valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unsupported ingestion run status: " + rawValue, error);
        }
    }
}
