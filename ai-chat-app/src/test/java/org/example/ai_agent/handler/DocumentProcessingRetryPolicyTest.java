package org.example.ai_agent.handler;

import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.service.impl.document.DocumentParsingLimitExceededException;
import org.junit.jupiter.api.Test;

class DocumentProcessingRetryPolicyTest {

    private final DocumentProcessingRetryPolicy retryPolicy = new DocumentProcessingRetryPolicy();

    @Test
    void isDocumentNotFoundError_shouldRecognizeKnownMessages() {
        org.junit.jupiter.api.Assertions.assertTrue(
                retryPolicy.isDocumentNotFoundError(new IllegalArgumentException("文档不存在: id=4")));
        org.junit.jupiter.api.Assertions.assertTrue(
                retryPolicy.isDocumentNotFoundError(new RuntimeException("document not found")));
        org.junit.jupiter.api.Assertions.assertFalse(
                retryPolicy.isDocumentNotFoundError(new RuntimeException("embedding failed")));
    }

    @Test
    void prepareRetry_shouldIncrementCountAndStoreError() {
        DocumentProcessingMessage message =
                DocumentProcessingMessage.createParseMessage(1L, 10L, "auto", true);

        retryPolicy.prepareRetry(message, "failed");

        org.junit.jupiter.api.Assertions.assertEquals(1, message.getRetryCount());
        org.junit.jupiter.api.Assertions.assertEquals("failed", message.getErrorMessage());
    }

    @Test
    void isRetryExhausted_shouldFollowConfiguredMaxAttempts() {
        DocumentProcessingMessage message =
                DocumentProcessingMessage.createParseMessage(1L, 10L, "auto", true);
        message.setRetryCount(DocumentProcessingConstants.Retry.MAX_ATTEMPTS);

        org.junit.jupiter.api.Assertions.assertTrue(retryPolicy.isRetryExhausted(message));
    }

    @Test
    void retryRoutingKeyByProcessingType_shouldMapKnownStages() {
        org.junit.jupiter.api.Assertions.assertEquals(
                DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE_RETRY,
                retryPolicy.retryRoutingKeyByProcessingType("PARSE"));
        org.junit.jupiter.api.Assertions.assertEquals(
                DocumentProcessingConstants.RoutingKey.DOCUMENT_CHUNK_RETRY,
                retryPolicy.retryRoutingKeyByProcessingType("CHUNK"));
        org.junit.jupiter.api.Assertions.assertEquals(
                DocumentProcessingConstants.RoutingKey.DOCUMENT_EMBED_RETRY,
                retryPolicy.retryRoutingKeyByProcessingType("EMBED"));
    }

    @Test
    void isNonRetryableError_shouldRejectTextLimitExceeded() {
        org.junit.jupiter.api.Assertions.assertTrue(
                retryPolicy.isNonRetryableError(new DocumentParsingLimitExceededException(1_000_000)));
    }
}
