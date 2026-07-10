package org.example.ai_agent.service.impl.document;

import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publish_shouldSendThroughRabbitTemplate() {
        DocumentProcessingPublisher publisher = new DocumentProcessingPublisher(rabbitTemplate);
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(1L, 10L, "auto", true);

        StepVerifier.create(publisher.publish(message, DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE))
                .expectNext(true)
                .verifyComplete();

        verify(rabbitTemplate).convertAndSend(
                DocumentProcessingConstants.Exchange.DOCUMENT_PROCESSING,
                DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE,
                message);
    }

    @Test
    void publish_shouldReturnFalseWhenRabbitTemplateFails() {
        DocumentProcessingPublisher publisher = new DocumentProcessingPublisher(rabbitTemplate);
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(1L, 10L, "auto", true);
        doThrow(new RuntimeException("rabbit down")).when(rabbitTemplate).convertAndSend(
                DocumentProcessingConstants.Exchange.DOCUMENT_PROCESSING,
                DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE,
                message);

        StepVerifier.create(publisher.publish(message, DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE))
                .expectNext(false)
                .verifyComplete();
    }
}
