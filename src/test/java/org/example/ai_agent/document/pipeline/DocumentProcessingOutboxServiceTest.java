package org.example.ai_agent.document.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.ai_agent.document.config.DocumentProcessingProperties;
import org.example.ai_agent.document.dto.DocumentProcessingMessage;
import org.example.ai_agent.document.entity.DocumentProcessingOutboxEvent;
import org.example.ai_agent.document.repository.DocumentProcessingOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingOutboxServiceTest {

    @Mock
    private DocumentProcessingOutboxRepository outboxRepository;
    @Mock
    private DocumentProcessingPublisher publisher;

    private ObjectMapper objectMapper;
    private DocumentProcessingProperties properties;
    private DocumentProcessingOutboxService service;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        properties = new DocumentProcessingProperties();
        meterRegistry = new SimpleMeterRegistry();
        service = new DocumentProcessingOutboxService(outboxRepository, publisher, objectMapper, properties, meterRegistry);
    }

    @Test
    void enqueue_shouldPersistTheProcessingMessageBeforeReportingSuccess() throws Exception {
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(1L, 10L, "auto", true);
        message.setRunId("00000000-0000-0000-0000-000000000008");
        message.setVersionId(81L);
        message.setGeneration(2L);
        when(outboxRepository.save(any(DocumentProcessingOutboxEvent.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.enqueue(message, "document.parse"))
                .expectNext(true)
                .verifyComplete();

        ArgumentCaptor<DocumentProcessingOutboxEvent> eventCaptor =
                ArgumentCaptor.forClass(DocumentProcessingOutboxEvent.class);
        verify(outboxRepository).save(eventCaptor.capture());
        DocumentProcessingOutboxEvent event = eventCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(DocumentProcessingOutboxService.STATUS_PENDING, event.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("document.parse", event.getRoutingKey());
        org.junit.jupiter.api.Assertions.assertNotNull(event.getEventKey());
        org.junit.jupiter.api.Assertions.assertEquals(message.getRunId(), event.getRunId());
        org.junit.jupiter.api.Assertions.assertEquals(81L, event.getVersionId());
        org.junit.jupiter.api.Assertions.assertEquals(2L, event.getGeneration());
        org.junit.jupiter.api.Assertions.assertEquals(1L,
                objectMapper.readValue(event.getPayload(), DocumentProcessingMessage.class).getDocumentId());
        org.junit.jupiter.api.Assertions.assertEquals(message.getRunId(),
                objectMapper.readValue(event.getPayload(), DocumentProcessingMessage.class).getRunId());
        org.junit.jupiter.api.Assertions.assertEquals(1.0,
                meterRegistry.get("aichat.document.outbox.events").tag("outcome", "enqueued").counter().count());
    }

    @Test
    void dispatchPending_shouldMarkEventPublishedOnlyAfterBrokerConfirm() throws Exception {
        DocumentProcessingOutboxEvent event = event(7L, 0);
        when(outboxRepository.recoverStaleClaims(any(LocalDateTime.class))).thenReturn(Mono.just(0));
        when(outboxRepository.countPending()).thenReturn(Mono.just(1L));
        when(outboxRepository.findDispatchable(anyInt())).thenReturn(Flux.just(event));
        when(outboxRepository.claimForDispatch(7L)).thenReturn(Mono.just(1));
        when(publisher.publish(any(DocumentProcessingMessage.class), eq("document.parse"))).thenReturn(Mono.just(true));
        when(outboxRepository.markPublished(7L)).thenReturn(Mono.just(1));

        StepVerifier.create(service.dispatchPending()).verifyComplete();

        verify(outboxRepository).markPublished(7L);
        verify(outboxRepository, org.mockito.Mockito.never()).markRetry(anyLong(), any(), anyString());
    }

    @Test
    void dispatchPending_shouldKeepEventRetryableWhenBrokerDoesNotConfirm() throws Exception {
        DocumentProcessingOutboxEvent event = event(8L, 2);
        when(outboxRepository.recoverStaleClaims(any(LocalDateTime.class))).thenReturn(Mono.just(0));
        when(outboxRepository.countPending()).thenReturn(Mono.just(1L));
        when(outboxRepository.findDispatchable(anyInt())).thenReturn(Flux.just(event));
        when(outboxRepository.claimForDispatch(8L)).thenReturn(Mono.just(1));
        when(publisher.publish(any(DocumentProcessingMessage.class), eq("document.parse"))).thenReturn(Mono.just(false));
        when(outboxRepository.markRetry(eq(8L), any(LocalDateTime.class), anyString())).thenReturn(Mono.just(1));

        StepVerifier.create(service.dispatchPending()).verifyComplete();

        verify(outboxRepository).markRetry(eq(8L), any(LocalDateTime.class), anyString());
    }

    private DocumentProcessingOutboxEvent event(Long id, int attemptCount) throws Exception {
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(1L, 10L, "auto", true);
        DocumentProcessingOutboxEvent event = new DocumentProcessingOutboxEvent();
        event.setId(id);
        event.setDocumentId(1L);
        event.setKnowledgeBaseId(10L);
        event.setProcessingType("PARSE");
        event.setRoutingKey("document.parse");
        event.setPayload(objectMapper.writeValueAsString(message));
        event.setAttemptCount(attemptCount);
        return event;
    }
}
