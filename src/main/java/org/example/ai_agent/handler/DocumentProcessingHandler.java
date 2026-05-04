package org.example.ai_agent.handler;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.service.DocumentProcessingService;
import org.example.ai_agent.service.DocumentProcessingRecordService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

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
        
        // 记录失败信息，可以考虑发送通知或者存储到失败记录表
        recordProcessingFailure(message);
    }
    
    /**
     * 处理失败情况
     */
    private void handleProcessingFailure(DocumentProcessingMessage message, String errorMessage) {
        message.setErrorMessage(errorMessage);

        log.error("文档处理最终失败: documentId={}, processingType={}, retryCount={}, error={}",
                message.getDocumentId(), message.getProcessingType(), message.getRetryCount(), errorMessage);

        updateDocumentStatusToFailed(message, errorMessage);
        recordService.failProcessing(
                message.getDocumentId(),
                message.getProcessingType(),
                errorMessage,
                null)
            .subscribe();
    }
    
    /**
     * 更新文档状态为失败
     */
    private void updateDocumentStatusToFailed(DocumentProcessingMessage message, String errorMessage) {
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
        
        documentProcessingService.updateDocumentStatus(
                message.getDocumentId(), parseStatus, embeddingStatus, errorMessage)
            .subscribe(result -> {
                log.info("文档状态更新为失败: documentId={}, result={}", 
                        message.getDocumentId(), result);
            });
    }
    
    /**
     * 记录处理失败信息
     */
    private void recordProcessingFailure(DocumentProcessingMessage message) {
        // 保存到处理记录表
        recordService.failProcessing(
                message.getDocumentId(),
                message.getProcessingType(),
                message.getErrorMessage(),
                null)
            .subscribe(record -> {
                log.info("文档处理失败记录已保存: documentId={}, processingType={}, recordId={}",
                        message.getDocumentId(), message.getProcessingType(), record.getId());
            });
        
        log.error("文档处理最终失败: documentId={}, processingType={}, errorMessage={}", 
                message.getDocumentId(), message.getProcessingType(), message.getErrorMessage());
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
        processingMono.subscribe(result -> {
            if (Boolean.TRUE.equals(result)) {
                log.info("文档处理成功: documentId={}, processingType={}",
                        message.getDocumentId(), message.getProcessingType());
                ackMessage(channel, deliveryTag, message, "处理成功");
                return;
            }

            log.warn("文档处理返回失败: documentId={}, processingType={}, error={}",
                    message.getDocumentId(), message.getProcessingType(), failureMessage);
            scheduleRetryOrFail(message, failureMessage, channel, deliveryTag);
        }, error -> {
            log.error("文档处理异常: documentId={}, processingType={}, error={}",
                    message.getDocumentId(), message.getProcessingType(), error.getMessage(), error);

            if (isDocumentNotFoundError(error)) {
                log.warn("文档不存在，丢弃消息: documentId={}, processingType={}",
                        message.getDocumentId(), message.getProcessingType());
                recordDocumentNotFoundFailure(message, error.getMessage());
                ackMessage(channel, deliveryTag, message, "文档不存在");
                return;
            }

            scheduleRetryOrFail(message, error.getMessage(), channel, deliveryTag);
        });
    }

    private void scheduleRetryOrFail(DocumentProcessingMessage message, String errorMessage,
                                     Channel channel, long deliveryTag) {
        message.incrementRetryCount();
        message.setErrorMessage(errorMessage);

        if (message.getRetryCount() >= DocumentProcessingConstants.Retry.MAX_ATTEMPTS) {
            handleProcessingFailure(message, errorMessage);
            ackMessage(channel, deliveryTag, message, "最终失败");
            return;
        }

        String retryRoutingKey = getRetryRoutingKeyByProcessingType(message.getProcessingType());
        documentProcessingService.sendProcessingMessage(message, retryRoutingKey)
                .subscribe(result -> {
                    if (Boolean.TRUE.equals(result)) {
                        recordService.incrementRetryCount(message.getDocumentId(), message.getProcessingType())
                                .subscribe();
                        log.info("重试消息发送成功: documentId={}, processingType={}, retryCount={}",
                                message.getDocumentId(), message.getProcessingType(), message.getRetryCount());
                        ackMessage(channel, deliveryTag, message, "已投递重试队列");
                    } else {
                        log.error("重试消息发送失败，消息重新入队: documentId={}, processingType={}",
                                message.getDocumentId(), message.getProcessingType());
                        nackMessage(channel, deliveryTag, message, true, "重试投递失败");
                    }
                }, error -> {
                    log.error("重试消息发送异常，消息重新入队: documentId={}, processingType={}, error={}",
                            message.getDocumentId(), message.getProcessingType(), error.getMessage(), error);
                    nackMessage(channel, deliveryTag, message, true, "重试投递异常");
                });
    }

    private boolean ackMessage(Channel channel, long deliveryTag, DocumentProcessingMessage message, String scene) {
        try {
            channel.basicAck(deliveryTag, false);
            log.debug("消息确认成功: documentId={}, processingType={}, scene={}",
                    message.getDocumentId(), message.getProcessingType(), scene);
            return true;
        } catch (IOException e) {
            log.error("消息确认失败: documentId={}, processingType={}, scene={}, error={}",
                    message.getDocumentId(), message.getProcessingType(), scene, e.getMessage(), e);
            return false;
        }
    }

    private boolean nackMessage(Channel channel, long deliveryTag, DocumentProcessingMessage message,
                                boolean requeue, String scene) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
            log.debug("消息拒绝成功: documentId={}, processingType={}, scene={}, requeue={}",
                    message.getDocumentId(), message.getProcessingType(), scene, requeue);
            return true;
        } catch (IOException e) {
            log.error("消息拒绝失败: documentId={}, processingType={}, scene={}, error={}",
                    message.getDocumentId(), message.getProcessingType(), scene, e.getMessage(), e);
            return false;
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
    private void recordDocumentNotFoundFailure(DocumentProcessingMessage message, String errorMessage) {
        log.info("记录文档不存在的失败情况: documentId={}, processingType={}", 
                message.getDocumentId(), message.getProcessingType());
        
        // 不进行重试，直接记录为最终失败
        recordService.failProcessing(
                message.getDocumentId(),
                message.getProcessingType(),
                "文档不存在: " + errorMessage,
                null
        ).subscribe(
            record -> log.info("文档不存在失败记录已保存: documentId={}, recordId={}", 
                    message.getDocumentId(), record != null ? record.getId() : "null"),
            error -> log.error("保存文档不存在失败记录失败: documentId={}, error={}", 
                    message.getDocumentId(), error.getMessage())
        );
    }
}
