package org.example.ai_agent.service.impl.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.properties.DocumentProcessingProperties;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.entity.DocumentProcessingOutboxEvent;
import org.example.ai_agent.repository.document.DocumentProcessingOutboxRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文档流水线的持久化投递模块。
 * <p>接口的成功只表示消息已耐久化，不表示 RabbitMQ 已接收；投递器负责后续确认与重试。</p>
 */
@Slf4j
@Service
public class DocumentProcessingOutboxService {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_RETRY = "RETRY";

    private final DocumentProcessingOutboxRepository outboxRepository;
    private final DocumentProcessingPublisher publisher;
    private final ObjectMapper objectMapper;
    private final DocumentProcessingProperties properties;
    private final Counter enqueuedCounter;
    private final Counter publishedCounter;
    private final Counter retryCounter;
    private final Counter recoveredCounter;

    public DocumentProcessingOutboxService(DocumentProcessingOutboxRepository outboxRepository,
                                           DocumentProcessingPublisher publisher,
                                           ObjectMapper objectMapper,
                                           DocumentProcessingProperties properties,
                                           MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.enqueuedCounter = meterRegistry.counter("aichat.document.outbox.events", "outcome", "enqueued");
        this.publishedCounter = meterRegistry.counter("aichat.document.outbox.events", "outcome", "published");
        this.retryCounter = meterRegistry.counter("aichat.document.outbox.events", "outcome", "retry");
        this.recoveredCounter = meterRegistry.counter("aichat.document.outbox.events", "outcome", "recovered");
    }

    public Mono<Boolean> enqueue(DocumentProcessingMessage message, String routingKey) {
        return Mono.fromCallable(() -> toOutboxEvent(message, routingKey))
                .flatMap(outboxRepository::save)
                .doOnSuccess(event -> log.debug("文档消息已写入 outbox: eventId={}, documentId={}, routingKey={}",
                        event.getId(), event.getDocumentId(), event.getRoutingKey()))
                .doOnSuccess(event -> enqueuedCounter.increment())
                .map(ignored -> true);
    }

    public Mono<Void> dispatchPending() {
        LocalDateTime staleBefore = LocalDateTime.now().minus(properties.effectiveOutboxClaimTimeout());
        return outboxRepository.recoverStaleClaims(staleBefore)
                .doOnNext(recovered -> {
                    if (recovered > 0) {
                        recoveredCounter.increment(recovered);
                        log.warn("已恢复超时的 outbox 投递声明: count={}", recovered);
                    }
                })
                .thenMany(outboxRepository.findDispatchable(properties.effectiveOutboxBatchSize()))
                .concatMap(this::claimAndDispatch)
                .then();
    }

    private Mono<Void> claimAndDispatch(DocumentProcessingOutboxEvent event) {
        return outboxRepository.claimForDispatch(event.getId())
                .flatMap(claimed -> claimed != null && claimed > 0
                        ? dispatchClaimedEvent(event)
                        : Mono.empty());
    }

    private Mono<Void> dispatchClaimedEvent(DocumentProcessingOutboxEvent event) {
        return deserialize(event)
                .flatMap(message -> publisher.publish(message, event.getRoutingKey())
                        .flatMap(published -> Boolean.TRUE.equals(published)
                                ? outboxRepository.markPublished(event.getId())
                                    .doOnNext(updated -> {
                                        if (updated != null && updated > 0) {
                                            publishedCounter.increment();
                                        }
                                    })
                                : retry(event, "RabbitMQ did not confirm the message"))
                        .then())
                .onErrorResume(error -> retry(event, error.getMessage()).then());
    }

    private Mono<DocumentProcessingMessage> deserialize(DocumentProcessingOutboxEvent event) {
        return Mono.fromCallable(() -> objectMapper.readValue(event.getPayload(), DocumentProcessingMessage.class));
    }

    private Mono<Integer> retry(DocumentProcessingOutboxEvent event, String error) {
        LocalDateTime nextAttemptAt = LocalDateTime.now().plus(retryDelay(event.getAttemptCount()));
        String sanitizedError = sanitizeError(error);
        log.warn("outbox 投递失败，将重试: eventId={}, documentId={}, attempt={}, nextAttemptAt={}, error={}",
                event.getId(), event.getDocumentId(), event.getAttemptCount(), nextAttemptAt, sanitizedError);
        return outboxRepository.markRetry(event.getId(), nextAttemptAt, sanitizedError)
                .doOnNext(updated -> {
                    if (updated != null && updated > 0) {
                        retryCounter.increment();
                    }
                });
    }

    private DocumentProcessingOutboxEvent toOutboxEvent(DocumentProcessingMessage message, String routingKey)
            throws JsonProcessingException {
        if (message == null || message.getDocumentId() == null || message.getKnowledgeBaseId() == null
                || message.getProcessingType() == null || routingKey == null || routingKey.isBlank()) {
            throw new IllegalArgumentException("文档处理 outbox 消息不完整");
        }

        LocalDateTime now = LocalDateTime.now();
        DocumentProcessingOutboxEvent event = new DocumentProcessingOutboxEvent();
        event.setEventKey(UUID.randomUUID().toString());
        event.setDocumentId(message.getDocumentId());
        event.setKnowledgeBaseId(message.getKnowledgeBaseId());
        event.setProcessingType(message.getProcessingType());
        event.setRoutingKey(routingKey);
        event.setPayload(objectMapper.writeValueAsString(message));
        event.setStatus(STATUS_PENDING);
        event.setAttemptCount(0);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return event;
    }

    private Duration retryDelay(Integer attemptCount) {
        int attempts = Math.max(1, attemptCount == null ? 1 : attemptCount);
        long factor = 1L << Math.min(attempts - 1, 10);
        Duration candidate = properties.effectiveOutboxRetryDelay().multipliedBy(factor);
        Duration maximum = properties.effectiveOutboxMaxRetryDelay();
        return candidate.compareTo(maximum) > 0 ? maximum : candidate;
    }

    private String sanitizeError(String error) {
        if (error == null || error.isBlank()) {
            return "unknown dispatch failure";
        }
        String compact = error.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500);
    }
}
