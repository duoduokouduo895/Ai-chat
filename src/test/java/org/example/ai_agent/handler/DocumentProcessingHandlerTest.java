package org.example.ai_agent.handler;

import com.rabbitmq.client.Channel;
import org.example.ai_agent.config.properties.DocumentProcessingProperties;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.entity.DocumentProcessingRecord;
import org.example.ai_agent.service.DocumentProcessingRecordService;
import org.example.ai_agent.service.DocumentProcessingService;
import org.example.ai_agent.service.impl.document.DocumentProcessingAlreadyHandledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("high-risk")
class DocumentProcessingHandlerTest {

    @Mock
    private DocumentProcessingService documentProcessingService;
    @Mock
    private DocumentProcessingRecordService recordService;
    @Mock
    private Channel channel;

    private DocumentProcessingHandler handler;
    private DocumentProcessingProperties processingProperties;

    @BeforeEach
    void setUp() {
        processingProperties = new DocumentProcessingProperties();
        handler = new DocumentProcessingHandler(
                documentProcessingService,
                recordService,
                new DocumentProcessingRetryPolicy(),
                new DocumentProcessingAckHandler(),
                processingProperties);
    }

    @Test
    void handleDocumentParsing_shouldAckOnSuccess() throws IOException {
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(1L, 10L, "auto", true);

        when(documentProcessingService.processDocumentParsing(message)).thenReturn(Mono.just(true));

        handler.handleDocumentParsing(message, channel, 100L);

        verify(channel).basicAck(100L, false);
        verify(documentProcessingService, never()).sendProcessingMessage(any(), any());
    }

    @Test
    void handleDocumentParsing_shouldSendRetryMessageWhenProcessingReturnsFalse() throws IOException {
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(2L, 20L, "auto", true);

        when(documentProcessingService.processDocumentParsing(message)).thenReturn(Mono.just(false));
        when(documentProcessingService.sendProcessingMessage(message, DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE_RETRY))
                .thenReturn(Mono.just(true));
        when(recordService.incrementRetryCount(2L, "PARSE")).thenReturn(Mono.just(new DocumentProcessingRecord()));

        handler.handleDocumentParsing(message, channel, 200L);

        verify(documentProcessingService).sendProcessingMessage(message, DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE_RETRY);
        verify(recordService).incrementRetryCount(2L, "PARSE");
        verify(channel).basicAck(200L, false);
    }

    @Test
    void handleDocumentEmbedding_shouldMarkFinalFailureWhenRetryLimitReached() throws IOException {
        DocumentProcessingMessage message = DocumentProcessingMessage.createEmbedMessage(3L, 30L);
        message.setRetryCount(DocumentProcessingConstants.Retry.MAX_ATTEMPTS - 1);

        when(documentProcessingService.processDocumentEmbedding(message))
                .thenReturn(Mono.error(new RuntimeException("embedding failed")));
        when(documentProcessingService.updateDocumentStatus(3L, null, DocumentProcessingConstants.Status.FAILED, "embedding failed"))
                .thenReturn(Mono.just(true));
        when(recordService.failProcessing(3L, "EMBED", "embedding failed", null))
                .thenReturn(Mono.just(new DocumentProcessingRecord()));

        handler.handleDocumentEmbedding(message, channel, 300L);

        verify(recordService).failProcessing(3L, "EMBED", "embedding failed", null);
        verify(documentProcessingService).updateDocumentStatus(3L, null, DocumentProcessingConstants.Status.FAILED, "embedding failed");
        verify(documentProcessingService, never()).sendProcessingMessage(any(), any());
        verify(channel).basicAck(300L, false);
    }

    @Test
    void handleDocumentChunking_shouldDiscardNotFoundDocumentWithoutRetry() throws IOException {
        DocumentProcessingMessage message = DocumentProcessingMessage.createChunkMessage(4L, 40L, true);

        when(documentProcessingService.processDocumentChunking(message))
                .thenReturn(Mono.error(new IllegalArgumentException("文档不存在: id=4")));
        when(recordService.failProcessing(4L, "CHUNK", "文档不存在: 文档不存在: id=4", null))
                .thenReturn(Mono.just(new DocumentProcessingRecord()));

        handler.handleDocumentChunking(message, channel, 400L);

        verify(recordService).failProcessing(4L, "CHUNK", "文档不存在: 文档不存在: id=4", null);
        verify(documentProcessingService, never()).sendProcessingMessage(any(), any());
        verify(channel).basicAck(400L, false);
    }

    @Test
    void handleDocumentParsing_shouldAckDuplicateWithoutRetry() throws IOException {
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(5L, 50L, "auto", true);

        when(documentProcessingService.processDocumentParsing(message))
                .thenReturn(Mono.error(new DocumentProcessingAlreadyHandledException(5L, "PARSE", "COMPLETED")));

        handler.handleDocumentParsing(message, channel, 500L);

        verify(documentProcessingService, never()).sendProcessingMessage(any(), any());
        verify(channel).basicAck(500L, false);
    }

    @Test
    void handleDocumentParsing_shouldDeferInFlightDuplicate() throws IOException {
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(6L, 60L, "auto", true);

        when(documentProcessingService.processDocumentParsing(message))
                .thenReturn(Mono.error(new DocumentProcessingAlreadyHandledException(6L, "PARSE", "PROCESSING")));
        when(documentProcessingService.sendProcessingMessage(
                message, DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE_RETRY))
                .thenReturn(Mono.just(true));

        handler.handleDocumentParsing(message, channel, 600L);

        verify(documentProcessingService).sendProcessingMessage(
                message, DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE_RETRY);
        verify(recordService, never()).incrementRetryCount(6L, "PARSE");
        verify(channel).basicAck(600L, false);
    }

    @Test
    void handleDocumentParsing_shouldNackWhenRetryMessageCannotBePublished() throws IOException {
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(7L, 70L, "auto", true);

        when(documentProcessingService.processDocumentParsing(message)).thenReturn(Mono.just(false));
        when(recordService.incrementRetryCount(7L, "PARSE"))
                .thenReturn(Mono.just(new DocumentProcessingRecord()));
        when(documentProcessingService.sendProcessingMessage(
                message, DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE_RETRY))
                .thenReturn(Mono.just(false));

        handler.handleDocumentParsing(message, channel, 700L);

        verify(channel).basicNack(700L, false, true);
        verify(channel, never()).basicAck(700L, false);
    }

    @Test
    void handleDocumentParsing_shouldRetryWhenConsumerTimesOut() throws IOException {
        processingProperties.setConsumerTimeout(Duration.ofMillis(10));
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(8L, 80L, "auto", true);

        when(documentProcessingService.processDocumentParsing(message)).thenReturn(Mono.never());
        when(recordService.incrementRetryCount(8L, "PARSE"))
                .thenReturn(Mono.just(new DocumentProcessingRecord()));
        when(documentProcessingService.sendProcessingMessage(
                message, DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE_RETRY))
                .thenReturn(Mono.just(true));

        handler.handleDocumentParsing(message, channel, 800L);

        verify(documentProcessingService).sendProcessingMessage(
                message, DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE_RETRY);
        verify(channel).basicAck(800L, false);
    }

    @Test
    void handleDocumentEmbedding_shouldNotAckWhenFailureRecordCannotBeSaved() throws IOException {
        DocumentProcessingMessage message = DocumentProcessingMessage.createEmbedMessage(9L, 90L);
        message.setRetryCount(DocumentProcessingConstants.Retry.MAX_ATTEMPTS - 1);

        when(documentProcessingService.processDocumentEmbedding(message))
                .thenReturn(Mono.error(new RuntimeException("embedding failed")));
        when(documentProcessingService.updateDocumentStatus(9L, null, DocumentProcessingConstants.Status.FAILED, "embedding failed"))
                .thenReturn(Mono.just(true));
        when(recordService.failProcessing(9L, "EMBED", "embedding failed", null))
                .thenReturn(Mono.error(new IllegalStateException("record unavailable")));

        Assertions.assertThrows(IllegalStateException.class,
                () -> handler.handleDocumentEmbedding(message, channel, 900L));

        verify(channel, never()).basicAck(900L, false);
    }
}
