package org.example.ai_agent.handler;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Slf4j
@Component
public class DocumentProcessingAckHandler {

    public Mono<Void> ack(Channel channel, long deliveryTag, DocumentProcessingMessage message, String scene) {
        try {
            channel.basicAck(deliveryTag, false);
            log.debug("消息确认成功: documentId={}, processingType={}, scene={}",
                    message.getDocumentId(), message.getProcessingType(), scene);
            return Mono.empty();
        } catch (IOException e) {
            log.error("消息确认失败: documentId={}, processingType={}, scene={}, errorType={}",
                    message.getDocumentId(), message.getProcessingType(), scene, e.getClass().getSimpleName());
            return Mono.error(e);
        }
    }

    public Mono<Void> nack(Channel channel,
                           long deliveryTag,
                           DocumentProcessingMessage message,
                           boolean requeue,
                           String scene) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
            log.debug("消息拒绝成功: documentId={}, processingType={}, scene={}, requeue={}",
                    message.getDocumentId(), message.getProcessingType(), scene, requeue);
            return Mono.empty();
        } catch (IOException e) {
            log.error("消息拒绝失败: documentId={}, processingType={}, scene={}, errorType={}",
                    message.getDocumentId(), message.getProcessingType(), scene, e.getClass().getSimpleName());
            return Mono.error(e);
        }
    }
}
