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
        
        documentProcessingService.processDocumentParsing(message)
            .doOnSuccess(result -> {
                try {
                    if (result) {
                        log.info("文档解析处理成功: documentId={}", message.getDocumentId());
                        // 手动确认消息
                        channel.basicAck(deliveryTag, false);
                    } else {
                        log.warn("文档解析处理失败: documentId={}", message.getDocumentId());
                        // 拒绝消息并重新入队
                        channel.basicNack(deliveryTag, false, true);
                        handleProcessingFailure(message, "文档解析处理失败");
                    }
                } catch (IOException e) {
                    log.error("消息确认失败: documentId={}, error={}", message.getDocumentId(), e.getMessage());
                }
            })
            .doOnError(error -> {
                try {
                    log.error("文档解析处理异常: documentId={}, error={}", 
                            message.getDocumentId(), error.getMessage(), error);
                    
                    // 检查是否是文档不存在的错误，如果是则直接丢弃消息
                    if (isDocumentNotFoundError(error)) {
                        log.warn("文档不存在，丢弃消息: documentId={}", message.getDocumentId());
                        recordDocumentNotFoundFailure(message, error.getMessage());
                        // 确认消息（丢弃）
                        channel.basicAck(deliveryTag, false);
                        return;
                    }
                    
                    // 其他错误，拒绝消息并重新入队
                    channel.basicNack(deliveryTag, false, true);
                    handleProcessingFailure(message, error.getMessage());
                } catch (IOException e) {
                    log.error("消息拒绝失败: documentId={}, error={}", message.getDocumentId(), e.getMessage());
                }
            })
            .subscribe();
    }
    
    /**
     * 处理文档分块消息
     */
    @RabbitListener(queues = DocumentProcessingConstants.Queue.DOCUMENT_CHUNK)
    public void handleDocumentChunking(DocumentProcessingMessage message, Channel channel, 
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("接收到文档分块消息: documentId={}", message.getDocumentId());
        
        documentProcessingService.processDocumentChunking(message)
            .doOnSuccess(result -> {
                try {
                    if (result) {
                        log.info("文档分块处理成功: documentId={}", message.getDocumentId());
                        // 手动确认消息
                        channel.basicAck(deliveryTag, false);
                    } else {
                        log.warn("文档分块处理失败: documentId={}", message.getDocumentId());
                        // 拒绝消息并重新入队
                        channel.basicNack(deliveryTag, false, true);
                        handleProcessingFailure(message, "文档分块处理失败");
                    }
                } catch (IOException e) {
                    log.error("消息确认失败: documentId={}, error={}", message.getDocumentId(), e.getMessage());
                }
            })
            .doOnError(error -> {
                try {
                    log.error("文档分块处理异常: documentId={}, error={}", 
                            message.getDocumentId(), error.getMessage(), error);
                    
                    // 检查是否是文档不存在的错误，如果是则直接丢弃消息
                    if (isDocumentNotFoundError(error)) {
                        log.warn("文档不存在，丢弃消息: documentId={}", message.getDocumentId());
                        recordDocumentNotFoundFailure(message, error.getMessage());
                        // 确认消息（丢弃）
                        channel.basicAck(deliveryTag, false);
                        return;
                    }
                    
                    // 其他错误，拒绝消息并重新入队
                    channel.basicNack(deliveryTag, false, true);
                    handleProcessingFailure(message, error.getMessage());
                } catch (IOException e) {
                    log.error("消息拒绝失败: documentId={}, error={}", message.getDocumentId(), e.getMessage());
                }
            })
            .subscribe();
    }
    
    /**
     * 处理文档向量化消息
     */
    @RabbitListener(queues = DocumentProcessingConstants.Queue.DOCUMENT_EMBED)
    public void handleDocumentEmbedding(DocumentProcessingMessage message, Channel channel, 
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("接收到文档向量化消息: documentId={}", message.getDocumentId());
        
        documentProcessingService.processDocumentEmbedding(message)
            .doOnSuccess(result -> {
                try {
                    if (result) {
                        log.info("文档向量化处理成功: documentId={}", message.getDocumentId());
                        // 手动确认消息
                        channel.basicAck(deliveryTag, false);
                    } else {
                        log.warn("文档向量化处理失败: documentId={}", message.getDocumentId());
                        // 拒绝消息并重新入队
                        channel.basicNack(deliveryTag, false, true);
                        handleProcessingFailure(message, "文档向量化处理失败");
                    }
                } catch (IOException e) {
                    log.error("消息确认失败: documentId={}, error={}", message.getDocumentId(), e.getMessage());
                }
            })
            .doOnError(error -> {
                try {
                    log.error("文档向量化处理异常: documentId={}, error={}", 
                            message.getDocumentId(), error.getMessage(), error);
                    
                    // 检查是否是文档不存在的错误，如果是则直接丢弃消息
                    if (isDocumentNotFoundError(error)) {
                        log.warn("文档不存在，丢弃消息: documentId={}", message.getDocumentId());
                        recordDocumentNotFoundFailure(message, error.getMessage());
                        // 确认消息（丢弃）
                        channel.basicAck(deliveryTag, false);
                        return;
                    }
                    
                    // 其他错误，拒绝消息并重新入队
                    channel.basicNack(deliveryTag, false, true);
                    handleProcessingFailure(message, error.getMessage());
                } catch (IOException e) {
                    log.error("消息拒绝失败: documentId={}, error={}", message.getDocumentId(), e.getMessage());
                }
            })
            .subscribe();
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
        message.incrementRetryCount();
        message.setErrorMessage(errorMessage);
        
        // 增加重试记录
        recordService.incrementRetryCount(message.getDocumentId(), message.getProcessingType())
            .subscribe();
        
        log.warn("文档处理失败: documentId={}, processingType={}, retryCount={}, error={}", 
                message.getDocumentId(), message.getProcessingType(), message.getRetryCount(), errorMessage);
        
        // 检查是否超过最大重试次数或连续失败太多次
        boolean shouldStop = message.getRetryCount() >= DocumentProcessingConstants.Retry.MAX_ATTEMPTS;
        
        // 如果连续失败太多次（可能是文档已被删除），直接放弃
        if (message.getRetryCount() >= 3) {
            log.warn("文档处理连续失败太多次，可能是文档已被删除，放弃处理: documentId={}, retryCount={}", 
                    message.getDocumentId(), message.getRetryCount());
            shouldStop = true;
        }
        
        if (shouldStop) {
            log.error("文档处理超过最大重试次数或连续失败: documentId={}, processingType={}, retryCount={}", 
                    message.getDocumentId(), message.getProcessingType(), message.getRetryCount());
            
            // 更新文档状态为失败
            updateDocumentStatusToFailed(message, errorMessage);
            
            // 记录最终失败
            recordService.failProcessing(
                    message.getDocumentId(), 
                    message.getProcessingType(), 
                    errorMessage, 
                    null)
                .subscribe();
                
        } else {
            log.info("准备重试文档处理: documentId={}, processingType={}, retryCount={}", 
                    message.getDocumentId(), message.getProcessingType(), message.getRetryCount());
            
            // 延迟重试
            retryProcessing(message);
        }
    }
    
    /**
     * 重试处理
     */
    private void retryProcessing(DocumentProcessingMessage message) {
        try {
            // 延迟一段时间后重试
            Thread.sleep(DocumentProcessingConstants.Retry.RETRY_DELAY_MS);
            
            String routingKey = getRoutingKeyByProcessingType(message.getProcessingType());
            documentProcessingService.sendProcessingMessage(message, routingKey)
                .subscribe(result -> {
                    if (result) {
                        log.info("重试消息发送成功: documentId={}, processingType={}", 
                                message.getDocumentId(), message.getProcessingType());
                    } else {
                        log.error("重试消息发送失败: documentId={}, processingType={}", 
                                message.getDocumentId(), message.getProcessingType());
                    }
                });
                
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("重试延迟中断: documentId={}, error={}", message.getDocumentId(), e.getMessage());
        }
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
     * 根据处理类型获取路由键
     */
    private String getRoutingKeyByProcessingType(String processingType) {
        return switch (processingType) {
            case "PARSE" -> DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE;
            case "CHUNK" -> DocumentProcessingConstants.RoutingKey.DOCUMENT_CHUNK;
            case "EMBED" -> DocumentProcessingConstants.RoutingKey.DOCUMENT_EMBED;
            default -> throw new IllegalArgumentException("未知的处理类型: " + processingType);
        };
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
            error instanceof IllegalArgumentException ||
            error instanceof RuntimeException ||
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