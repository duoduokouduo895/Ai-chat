package org.example.ai_agent.handler;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DocumentProcessingRetryPolicy {

    public boolean isDocumentNotFoundError(Throwable error) {
        if (error == null) {
            return false;
        }

        String message = error.getMessage();
        if (message == null) {
            message = "";
        }

        boolean notFound =
                message.contains("does not exist") ||
                        message.contains("不存在") ||
                        message.contains("cannot start processing") ||
                        message.contains("Document with id") ||
                        message.contains("文档不存在") ||
                        message.contains("找不到文档") ||
                        message.contains("无法找到") ||
                        message.contains("文档已删除") ||
                        message.contains("no document found") ||
                        message.contains("document not found");

        if (notFound) {
            log.info("检测到文档不存在错误: errorType={}, errorMessage={}",
                    error.getClass().getSimpleName(), message);
        }

        return notFound;
    }

    public void prepareRetry(DocumentProcessingMessage message, String errorMessage) {
        message.incrementRetryCount();
        message.setErrorMessage(errorMessage);
    }

    public boolean isRetryExhausted(DocumentProcessingMessage message) {
        return message.getRetryCount() >= DocumentProcessingConstants.Retry.MAX_ATTEMPTS;
    }

    public String retryRoutingKeyByProcessingType(String processingType) {
        return switch (processingType) {
            case "PARSE" -> DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE_RETRY;
            case "CHUNK" -> DocumentProcessingConstants.RoutingKey.DOCUMENT_CHUNK_RETRY;
            case "EMBED" -> DocumentProcessingConstants.RoutingKey.DOCUMENT_EMBED_RETRY;
            default -> throw new IllegalArgumentException("未知的处理类型: " + processingType);
        };
    }
}
