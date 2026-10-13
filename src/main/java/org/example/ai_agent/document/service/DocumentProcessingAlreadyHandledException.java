package org.example.ai_agent.document.service;

import org.example.ai_agent.document.domain.DocumentProcessingStatus;

/**
 * 表示同一文档阶段已经在处理或已经完成。
 *
 * RabbitMQ 重复投递遇到该信号时可以直接确认消息，避免重复执行副作用。
 */
public class DocumentProcessingAlreadyHandledException extends RuntimeException {

    private final String status;

    public DocumentProcessingAlreadyHandledException(Long documentId, String processingType, String status) {
        super("文档处理阶段已被占用或已完成: documentId=" + documentId
                + ", processingType=" + processingType + ", status=" + status);
        this.status = status;
    }

    public boolean isCompleted() {
        try {
            return DocumentProcessingStatus.fromValue(status) == DocumentProcessingStatus.COMPLETED;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
