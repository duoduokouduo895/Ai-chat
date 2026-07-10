package org.example.ai_agent.service.impl.document;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class DocumentProcessingPublisher {

    private final RabbitTemplate rabbitTemplate;

    public DocumentProcessingPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public Mono<Boolean> publish(DocumentProcessingMessage message, String routingKey) {
        return Mono.fromCallable(() -> {
            try {
                rabbitTemplate.convertAndSend(
                        DocumentProcessingConstants.Exchange.DOCUMENT_PROCESSING,
                        routingKey,
                        message
                );

                log.debug("发送处理消息成功: routingKey={}, documentId={}, processingType={}",
                        routingKey, message.getDocumentId(), message.getProcessingType());

                return true;
            } catch (Exception e) {
                log.error("发送处理消息失败: routingKey={}, documentId={}, error={}",
                        routingKey, message.getDocumentId(), e.getMessage(), e);
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
