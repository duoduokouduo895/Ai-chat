package org.example.ai_agent.handler;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.service.DocumentProcessingService;
import org.example.ai_agent.service.DocumentProcessingRecordService;
import org.example.ai_agent.service.impl.document.DocumentProcessingAlreadyHandledException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;

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
    
    @Autowired
    private DocumentProcessingService documentProcessingService;
    
    @Autowired
    private DocumentProcessingRecordService recordService;
    
    /**
     * 处理文档解析消息
     */
    @RabbitListener(queues = DocumentProcessingConstants.Queue.DOCUMENT_PARSE)
    public void handleDocumentParsing(DocumentProcessingMessage message, Channel channel, 
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("接收到文档解析消息: documentId={}, parseMode={}", 
                message.getDocumentId(), message.getParseMode());

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

        processMessage(message, channel, deliveryTag,
                documentProcessingService.processDocumentEmbedding(message),
                "文档向量化处理失败");
    }
    
    /**
     * 处理死信队列消息
     */
    @RabbitListener(queues = DocumentProcessingConstants.Queue.DOCUMENT_DLQ)
    public void handleDeadLetterMessage(DocumentProcessingMessage message) {
        log.error("接收到死信队列消息: documentId={}, processingType={}, retryCount={}, errorMessage={}", 
                message.getDocumentId(), message.getProcessingType(), 
                message.getRetryCount(), message.getErrorMessage());
        
        try {
            recordProcessingFailure(message).block();
        } catch (RuntimeException error) {
            log.error("保存死信处理记录失败: documentId={}, error={}",
                    message.getDocumentId(), error.getMessage(), error);
        }
    }
    
    /**
     * 处理失败情况
     */
    private Mono<Void> handleProcessingFailure(DocumentProcessingMessage message, String errorMessage) {
        message.setErrorMessage(errorMessage);

        log.error("文档处理最终失败: documentId={}, processingType={}, retryCount={}, error={}",
                message.getDocumentId(), message.getProcessingType(), message.getRetryCount(), errorMessage);

        return updateDocumentStatusToFailed(message, errorMessage)
            .then(recordService.failProcessing(
                message.getDocumentId(),
                message.getProcessingType(),
                errorMessage,
                null))
            .then();
    }
    
    /**
     * 更新文档状态为失败
     */
    private Mono<Void> updateDocumentStatusToFailed(DocumentProcessingMessage message, String errorMessage) {
        String parseStatus = null;
        String embeddingStatus = null;
        
        switch (message.getProcessingType()) {
            case "PARSE":
                parseStatus = DocumentProcessingConstants.Status.FAILED;
                break;
            case "EMBED":
                embeddingStatus = DocumentProcessingConstants.Status.FAILED;
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
            .doOnNext(record -> log.info("文档处理失败记录已保存: documentId={}, processingType={}, recordId={}",
                    message.getDocumentId(), message.getProcessingType(), record.getId()))
            .doOnSubscribe(subscription -> log.error("文档处理最终失败: documentId={}, processingType={}, errorMessage={}",
                    message.getDocumentId(), message.getProcessingType(), message.getErrorMessage()))
            .then();
    }
    
    /**
     * 检查是否是文档不存在的错误
     */
    private boolean isDocumentNotFoundError(Throwable error) {
        if (error == null) return false;
        
        String message = error.getMessage();
        if (message == null) message = "";
        
        // 检查各种可能的文档不存在错误
        boolean isNotFound =
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
        
        if (isNotFound) {
            log.info("检测到文档不存在错误: documentId={}, errorType={}, errorMessage={}", 
                    "", error.getClass().getSimpleName(), message);
        }
        
        return isNotFound;
    }

    private void processMessage(DocumentProcessingMessage message, Channel channel, long deliveryTag,
                                reactor.core.publisher.Mono<Boolean> processingMono, String failureMessage) {
        processingMono
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
                log.error("文档处理异常: documentId={}, processingType={}, error={}",
                        message.getDocumentId(), message.getProcessingType(), error.getMessage(), error);

                if (isDocumentNotFoundError(error)) {
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
            .block();
    }

    private Mono<Void> scheduleRetryOrFail(DocumentProcessingMessage message, String errorMessage,
                                     Channel channel, long deliveryTag) {
        message.incrementRetryCount();
        message.setErrorMessage(errorMessage);

        if (message.getRetryCount() >= DocumentProcessingConstants.Retry.MAX_ATTEMPTS) {
            return handleProcessingFailure(message, errorMessage)
                    .then(ackMessage(channel, deliveryTag, message, "最终失败"));
        }

        String retryRoutingKey = getRetryRoutingKeyByProcessingType(message.getProcessingType());
        return recordService.incrementRetryCount(message.getDocumentId(), message.getProcessingType())
            .then(documentProcessingService.sendProcessingMessage(message, retryRoutingKey))
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
                    log.error("重试消息发送异常，消息重新入队: documentId={}, processingType={}, error={}",
                            message.getDocumentId(), message.getProcessingType(), error.getMessage(), error);
                    return nackMessage(channel, deliveryTag, message, true, "重试投递异常");
                });
    }

    private Mono<Void> deferInFlightMessage(DocumentProcessingMessage message, Channel channel, long deliveryTag) {
        String retryRoutingKey = getRetryRoutingKeyByProcessingType(message.getProcessingType());
        return documentProcessingService.sendProcessingMessage(message, retryRoutingKey)
                .flatMap(sent -> sent
                        ? ackMessage(channel, deliveryTag, message, "处理中消息延迟重投")
                        : nackMessage(channel, deliveryTag, message, true, "处理中消息延迟重投失败"));
    }

    private Mono<Void> ackMessage(Channel channel, long deliveryTag, DocumentProcessingMessage message, String scene) {
        try {
            channel.basicAck(deliveryTag, false);
            log.debug("消息确认成功: documentId={}, processingType={}, scene={}",
                    message.getDocumentId(), message.getProcessingType(), scene);
            return Mono.empty();
        } catch (IOException e) {
            log.error("消息确认失败: documentId={}, processingType={}, scene={}, error={}",
                    message.getDocumentId(), message.getProcessingType(), scene, e.getMessage(), e);
            return Mono.error(e);
        }
    }

    private Mono<Void> nackMessage(Channel channel, long deliveryTag, DocumentProcessingMessage message,
                                boolean requeue, String scene) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
            log.debug("消息拒绝成功: documentId={}, processingType={}, scene={}, requeue={}",
                    message.getDocumentId(), message.getProcessingType(), scene, requeue);
            return Mono.empty();
        } catch (IOException e) {
            log.error("消息拒绝失败: documentId={}, processingType={}, scene={}, error={}",
                    message.getDocumentId(), message.getProcessingType(), scene, e.getMessage(), e);
            return Mono.error(e);
        }
    }

    private String getRetryRoutingKeyByProcessingType(String processingType) {
        return switch (processingType) {
            case "PARSE" -> DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE_RETRY;
            case "CHUNK" -> DocumentProcessingConstants.RoutingKey.DOCUMENT_CHUNK_RETRY;
            case "EMBED" -> DocumentProcessingConstants.RoutingKey.DOCUMENT_EMBED_RETRY;
            default -> throw new IllegalArgumentException("未知的处理类型: " + processingType);
        };
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
        .doOnNext(record -> log.info("文档不存在失败记录已保存: documentId={}, recordId={}",
                message.getDocumentId(), record != null ? record.getId() : "null"))
        .doOnError(error -> log.error("保存文档不存在失败记录失败: documentId={}, error={}",
                message.getDocumentId(), error.getMessage()))
        .onErrorResume(error -> Mono.empty())
        .then();
    }
}
