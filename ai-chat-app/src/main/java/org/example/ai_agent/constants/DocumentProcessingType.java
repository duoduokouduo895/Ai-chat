package org.example.ai_agent.constants;

import java.util.Locale;

/** Internal representation of the document-processing stages. */
public enum DocumentProcessingType {
    PARSE,
    CHUNK,
    EMBED;

    public static DocumentProcessingType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("文档处理类型不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("未知的文档处理类型: " + value, error);
        }
    }
}
