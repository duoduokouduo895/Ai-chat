package org.example.ai_agent.service.impl.document;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.properties.DocumentProcessingProperties;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DocumentProcessingPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final DocumentProcessingProperties processingProperties;

    public DocumentProcessingPublisher(RabbitTemplate rabbitTemplate,
                                       DocumentProcessingProperties processingProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.processingProperties = processingProperties;
    }

    public Mono<Boolean> publish(DocumentProcessingMessage message, String routingKey) {
        return Mono.fromCallable(() -> {
            try {
                CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
                rabbitTemplate.convertAndSend(
                        DocumentProcessingConstants.Exchange.DOCUMENT_PROCESSING,
                        routingKey,
                        message,
                        correlationData
                );

                CorrelationData.Confirm confirm = correlationData.getFuture().get(
                        processingProperties.effectivePublishConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
                if (!confirm.isAck()) {
                    log.error("RabbitMQ 拒绝处理消息: routingKey={}, documentId={}, reason={}",
                            routingKey, message.getDocumentId(), confirm.getReason());
                    return false;
                }
                if (correlationData.getReturned() != null) {
                    log.error("RabbitMQ 未路由处理消息: routingKey={}, documentId={}, replyCode={}",
                            routingKey, message.getDocumentId(), correlationData.getReturned().getReplyCode());
                    return false;
                }

                log.debug("RabbitMQ 已确认处理消息: routingKey={}, documentId={}, processingType={}",
                        routingKey, message.getDocumentId(), message.getProcessingType());

                return true;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                log.warn("等待 RabbitMQ 确认时被中断: routingKey={}, documentId={}",
                        routingKey, message.getDocumentId());
                return false;
            } catch (Exception e) {
                log.error("发送处理消息或等待确认失败: routingKey={}, documentId={}, error={}",
                        routingKey, message.getDocumentId(), e.getMessage(), e);
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
