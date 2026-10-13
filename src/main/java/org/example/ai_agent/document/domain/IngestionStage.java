package org.example.ai_agent.document.domain;

import java.util.Locale;

public enum IngestionStage {
    PARSE,
    CHUNK,
    EMBED;

    public static IngestionStage fromValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Ingestion stage is required");
        }
        try {
            return valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unsupported ingestion stage: " + rawValue, error);
        }
    }

    public boolean isAfter(IngestionStage other) {
        return other != null && ordinal() > other.ordinal();
    }
}
