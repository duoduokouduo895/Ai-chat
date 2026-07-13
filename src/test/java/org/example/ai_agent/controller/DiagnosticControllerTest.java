package org.example.ai_agent.controller;

import org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository;
import org.example.ai_agent.service.DocumentIngestionService;
import org.example.ai_agent.service.DocumentProcessingRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;

class DiagnosticControllerTest {

    @Test
    void reportsRabbitAdministrationUnavailableWhenAutoDeclarationIsDisabled() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        DiagnosticController controller = new DiagnosticController(
                mock(DocumentProcessingRecordService.class),
                mock(DocumentIngestionService.class),
                mock(KnowledgeDocumentRepository.class),
                mock(RabbitTemplate.class),
                beanFactory.getBeanProvider(AmqpAdmin.class));

        StepVerifier.create(controller.getRabbitMQQueueStatus())
                .expectNextMatches(result -> Boolean.FALSE.equals(result.get("success"))
                        && "RabbitMQ administration is disabled".equals(result.get("error")))
                .verifyComplete();
    }
}
