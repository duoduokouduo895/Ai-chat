package org.example.ai_agent.handler;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.constants.DocumentProcessingStatus;
import org.example.ai_agent.config.properties.DocumentProcessingProperties;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.service.DocumentProcessingService;
import org.example.ai_agent.service.DocumentProcessingRecordService;
import org.example.ai_agent.service.impl.document.DocumentProcessingAlreadyHandledException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 文档处理异步消息处理器
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Component
public class DocumentProcessingHandler {

    private final DocumentProcessingService documentProcessingService;
    private final DocumentProcessingRecordService recordService;
    private final DocumentProcessingRetryPolicy retryPolicy;
    private final DocumentProcessingAckHandler ackHandler;
    private final DocumentProcessingProperties processingProperties;

    public DocumentProcessingHandler(DocumentProcessingService documentProcessingService,
                                     DocumentProcessingRecordService recordService,
                                     DocumentProcessingRetryPolicy retryPolicy,
                                     DocumentProcessingAckHandler ackHandler,
                                     DocumentProcessingProperties processingProperties) {
        this.documentProcessingService = documentProcessingService;
        this.recordService = recordService;
        this.retryPolicy = retryPolicy;
        this.ackHandler = ackHandler;
        this.processingProperties = processingProperties;
    }
    
    /**
     * 处理文档解析消息
     */
    @RabbitListener(queues = DocumentProcessingConstants.Queue.DOCUMENT_PARSE)
    public void handleDocumentParsing(DocumentProcessingMessage message, Channel channel, 
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("接收到文档解析消息: documentId={}, parseMode={}", 
                message.getDocumentId(), message.getParseMode());
        validateMessage(message);

        processMessage(message, channel, deliveryTag,
                documentProcessingService.processDocumentParsing(message),
                "文档解析处理失败");
    }
    
    /**
     * 处理文档分块消息
     */
    @RabbitListener(queues = DocumentProcessingConstants.Queue.DOCUMENT_CHUNK)
    public void handleDocumentChunking(DocumentProcessingMessage message, Channel channel, 
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("接收到文档分块消息: documentId={}", message.getDocumentId());
        validateMessage(message);

        processMessage(message, channel, deliveryTag,
                documentProcessingService.processDocumentChunking(message),
                "文档分块处理失败");
    }
    
    /**
     * 处理文档向量化消息
     */
    @RabbitListener(queues = DocumentProcessingConstants.Queue.DOCUMENT_EMBED)
    public void handleDocumentEmbedding(DocumentProcessingMessage message, Channel channel, 
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("接收到文档向量化消息: documentId={}", message.getDocumentId());
        validateMessage(message);

        processMessage(message, channel, deliveryTag,
                documentProcessingService.processDocumentEmbedding(message),
                "文档向量化处理失败");
    }
    
    /**
     * 处理死信队列消息
     */
    @RabbitListener(queues = DocumentProcessingConstants.Queue.DOCUMENT_DLQ)
    public void handleDeadLetterMessage(DocumentProcessingMessage message) {
        log.error("接收到死信队列消息: documentId={}, processingType={}, retryCount={}",
                message.getDocumentId(), message.getProcessingType(), message.getRetryCount());
        
        try {
            recordProcessingFailure(message).block(processingTimeout());
        } catch (RuntimeException error) {
            log.error("保存死信处理记录失败，交由监听容器处理: documentId={}, errorType={}",
                    message.getDocumentId(), error.getClass().getSimpleName());
            throw error;
        }
    }
    
    /**
     * 处理失败情况
     */
    private Mono<Void> handleProcessingFailure(DocumentProcessingMessage message, String errorMessage) {
        message.setErrorMessage(errorMessage);

        log.error("文档处理最终失败: documentId={}, processingType={}, retryCount={}",
                message.getDocumentId(), message.getProcessingType(), message.getRetryCount());

        return updateDocumentStatusToFailed(message, errorMessage)
            .then(recordService.failProcessing(
                message.getDocumentId(),
                message.getProcessingType(),
                errorMessage,
                null)
                .switchIfEmpty(Mono.error(new IllegalStateException("未能保存文档处理失败记录"))))
            .then();
    }
    
    /**
     * 更新文档状态为失败
     */
    private Mono<Void> updateDocumentStatusToFailed(DocumentProcessingMessage message, String errorMessage) {
        String parseStatus = null;
        String embeddingStatus = null;
        
        switch (message.processingType()) {
            case PARSE:
                parseStatus = DocumentProcessingStatus.FAILED.value();
                break;
            case EMBED:
                embeddingStatus = DocumentProcessingStatus.FAILED.value();
                break;
            case CHUNK:
                break;
        }
        
        return documentProcessingService.updateDocumentStatus(
                message.getDocumentId(), parseStatus, embeddingStatus, errorMessage)
            .doOnNext(result -> log.info("文档状态更新为失败: documentId={}, result={}",
                    message.getDocumentId(), result))
            .then();
    }
    
    /**
     * 记录处理失败信息
     */
    private Mono<Void> recordProcessingFailure(DocumentProcessingMessage message) {
        return recordService.failProcessing(
                message.getDocumentId(),
                message.getProcessingType(),
                message.getErrorMessage(),
                null)
            .switchIfEmpty(Mono.error(new IllegalStateException("未能保存文档处理失败记录")))
            .doOnNext(record -> log.info("文档处理失败记录已保存: documentId={}, processingType={}, recordId={}",
                    message.getDocumentId(), message.getProcessingType(), record.getId()))
            .doOnSubscribe(subscription -> log.error("文档处理最终失败记录开始保存: documentId={}, processingType={}",
                    message.getDocumentId(), message.getProcessingType()))
            .then();
    }
    
    private void processMessage(DocumentProcessingMessage message, Channel channel, long deliveryTag,
                                reactor.core.publisher.Mono<Boolean> processingMono, String failureMessage) {
        Mono<Void> processingFlow = processingMono
            .switchIfEmpty(Mono.error(new IllegalStateException("文档处理未返回结果")))
            .timeout(processingTimeout())
            .flatMap(result -> {
                if (Boolean.TRUE.equals(result)) {
                    log.info("文档处理成功: documentId={}, processingType={}",
                            message.getDocumentId(), message.getProcessingType());
                    return ackMessage(channel, deliveryTag, message, "处理成功");
                }

                log.warn("文档处理返回失败: documentId={}, processingType={}, error={}",
                        message.getDocumentId(), message.getProcessingType(), failureMessage);
                return scheduleRetryOrFail(message, failureMessage, channel, deliveryTag);
            })
            .onErrorResume(error -> {
        log.error("文档处理异常: documentId={}, processingType={}, errorType={}",
                message.getDocumentId(), message.getProcessingType(), error.getClass().getSimpleName());

                if (retryPolicy.isNonRetryableError(error)) {
                    log.warn("文档处理遇到不可重试错误，直接标记失败: documentId={}, processingType={}",
                            message.getDocumentId(), message.getProcessingType());
                    return handleProcessingFailure(message, error.getMessage())
                            .then(ackMessage(channel, deliveryTag, message, "不可重试错误"));
                }

                if (retryPolicy.isDocumentNotFoundError(error)) {
                    log.warn("文档不存在，丢弃消息: documentId={}, processingType={}",
                            message.getDocumentId(), message.getProcessingType());
                    return recordDocumentNotFoundFailure(message, error.getMessage())
                            .then(ackMessage(channel, deliveryTag, message, "文档不存在"));
                }

                if (error instanceof DocumentProcessingAlreadyHandledException) {
                    DocumentProcessingAlreadyHandledException handled =
                            (DocumentProcessingAlreadyHandledException) error;
                    if (handled.isCompleted()) {
                        log.info("文档阶段已完成，确认重复消息: documentId={}, processingType={}",
                                message.getDocumentId(), message.getProcessingType());
                        return ackMessage(channel, deliveryTag, message, "重复消息");
                    }

                    log.info("文档阶段仍在处理中，延迟重投: documentId={}, processingType={}",
                            message.getDocumentId(), message.getProcessingType());
                    return deferInFlightMessage(message, channel, deliveryTag);
                }

                return scheduleRetryOrFail(message, error.getMessage(), channel, deliveryTag);
            })
            .timeout(processingTimeout().plusSeconds(5));

        processingFlow.block(processingTimeout().plusSeconds(10));
    }

    private Mono<Void> scheduleRetryOrFail(DocumentProcessingMessage message, String errorMessage,
                                     Channel channel, long deliveryTag) {
        retryPolicy.prepareRetry(message, errorMessage);

        if (retryPolicy.isRetryExhausted(message)) {
            return handleProcessingFailure(message, errorMessage)
                    .then(ackMessage(channel, deliveryTag, message, "最终失败"));
        }

        String retryRoutingKey = retryPolicy.retryRoutingKeyByProcessingType(message.getProcessingType());
        return recordService.incrementRetryCount(message.getDocumentId(), message.getProcessingType())
            .switchIfEmpty(Mono.error(new IllegalStateException("未能更新文档处理重试次数")))
            .then(documentProcessingService.sendProcessingMessage(message, retryRoutingKey)
                    .defaultIfEmpty(false))
                .flatMap(result -> {
                    if (Boolean.TRUE.equals(result)) {
                        log.info("重试消息发送成功: documentId={}, processingType={}, retryCount={}",
                                message.getDocumentId(), message.getProcessingType(), message.getRetryCount());
                        return ackMessage(channel, deliveryTag, message, "已投递重试队列");
                    }

                    log.error("重试消息发送失败，消息重新入队: documentId={}, processingType={}",
                            message.getDocumentId(), message.getProcessingType());
                    return nackMessage(channel, deliveryTag, message, true, "重试投递失败");
                })
                .onErrorResume(error -> {
                log.error("重试消息发送异常，消息重新入队: documentId={}, processingType={}, errorType={}",
                        message.getDocumentId(), message.getProcessingType(), error.getClass().getSimpleName());
                    return nackMessage(channel, deliveryTag, message, true, "重试投递异常");
                });
    }

    private Mono<Void> deferInFlightMessage(DocumentProcessingMessage message, Channel channel, long deliveryTag) {
        String retryRoutingKey = retryPolicy.retryRoutingKeyByProcessingType(message.getProcessingType());
        return documentProcessingService.sendProcessingMessage(message, retryRoutingKey)
                .defaultIfEmpty(false)
                .flatMap(sent -> sent
                        ? ackMessage(channel, deliveryTag, message, "处理中消息延迟重投")
                        : nackMessage(channel, deliveryTag, message, true, "处理中消息延迟重投失败"));
    }

    private Mono<Void> ackMessage(Channel channel, long deliveryTag, DocumentProcessingMessage message, String scene) {
        return ackHandler.ack(channel, deliveryTag, message, scene);
    }

    private Mono<Void> nackMessage(Channel channel, long deliveryTag, DocumentProcessingMessage message,
                                boolean requeue, String scene) {
        return ackHandler.nack(channel, deliveryTag, message, requeue, scene);
    }
    
    /**
     * 记录文档不存在的失败情况（不进行重试）
     */
    private Mono<Void> recordDocumentNotFoundFailure(DocumentProcessingMessage message, String errorMessage) {
        log.info("记录文档不存在的失败情况: documentId={}, processingType={}", 
                message.getDocumentId(), message.getProcessingType());
        
        return recordService.failProcessing(
                message.getDocumentId(),
                message.getProcessingType(),
                "文档不存在: " + errorMessage,
                null
        )
        .switchIfEmpty(Mono.error(new IllegalStateException("未能保存文档不存在失败记录")))
        .doOnNext(record -> log.info("文档不存在失败记录已保存: documentId={}, recordId={}",
                message.getDocumentId(), record != null ? record.getId() : "null"))
                .doOnError(error -> log.error("保存文档不存在失败记录失败: documentId={}, errorType={}",
                        message.getDocumentId(), error.getClass().getSimpleName()))
        .then();
    }

    private Duration processingTimeout() {
        return processingProperties.effectiveConsumerTimeout();
    }

    private void validateMessage(DocumentProcessingMessage message) {
        if (message == null || message.getDocumentId() == null || message.getDocumentId() <= 0) {
            throw new IllegalArgumentException("文档处理消息缺少有效文档ID");
        }
        message.processingType();
    }
}
