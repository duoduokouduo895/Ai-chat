package org.example.ai_agent.document.pipeline;

import org.example.ai_agent.document.config.DocumentProcessingProperties;
import org.example.ai_agent.document.domain.DocumentProcessingConstants;
import org.example.ai_agent.document.dto.DocumentProcessingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private DocumentProcessingProperties processingProperties;

    @BeforeEach
    void setUp() {
        processingProperties = new DocumentProcessingProperties();
        processingProperties.setPublishConfirmTimeout(Duration.ofSeconds(1));
    }

    @Test
    void publish_shouldSendThroughRabbitTemplate() {
        DocumentProcessingPublisher publisher = new DocumentProcessingPublisher(rabbitTemplate, processingProperties);
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(1L, 10L, "auto", true);
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(DocumentProcessingConstants.Exchange.DOCUMENT_PROCESSING),
                eq(DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE),
                eq(message), any(CorrelationData.class));

        StepVerifier.create(publisher.publish(message, DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE))
                .expectNext(true)
                .verifyComplete();

        verify(rabbitTemplate).convertAndSend(
                eq(DocumentProcessingConstants.Exchange.DOCUMENT_PROCESSING),
                eq(DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE),
                eq(message),
                any(CorrelationData.class));
    }

    @Test
    void publish_shouldReturnFalseWhenRabbitTemplateFails() {
        DocumentProcessingPublisher publisher = new DocumentProcessingPublisher(rabbitTemplate, processingProperties);
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(1L, 10L, "auto", true);
        doThrow(new RuntimeException("rabbit down")).when(rabbitTemplate).convertAndSend(
                eq(DocumentProcessingConstants.Exchange.DOCUMENT_PROCESSING),
                eq(DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE),
                eq(message),
                any(CorrelationData.class));

        StepVerifier.create(publisher.publish(message, DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void publish_shouldReturnFalseWhenBrokerNacksTheMessage() {
        DocumentProcessingPublisher publisher = new DocumentProcessingPublisher(rabbitTemplate, processingProperties);
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(1L, 10L, "auto", true);
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "exchange unavailable"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(DocumentProcessingConstants.Exchange.DOCUMENT_PROCESSING),
                eq(DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE),
                eq(message), any(CorrelationData.class));

        StepVerifier.create(publisher.publish(message, DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE))
                .expectNext(false)
                .verifyComplete();
    }
}
