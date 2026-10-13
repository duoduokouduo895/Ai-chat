package org.example.ai_agent.document.domain;

import java.util.Locale;

/** Internal status vocabulary; persistence and message contracts keep their existing strings. */
public enum DocumentProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public String value() {
        return name();
    }

    public static DocumentProcessingStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("文档处理状态不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("未知的文档处理状态: " + value, error);
        }
    }
}
