package org.example.ai_agent.document.controller;

import org.example.ai_agent.knowledge.repository.KnowledgeDocumentRepository;
import org.example.ai_agent.document.pipeline.DocumentIngestionService;
import org.example.ai_agent.document.service.DocumentProcessingRecordService;
import org.example.ai_agent.document.service.DocumentProcessingDiagnosticQueryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;

class DiagnosticControllerTest {

    @Test
    void reportsRabbitAdministrationUnavailableWhenAutoDeclarationIsDisabled() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        DiagnosticController controller = new DiagnosticController(
                new DocumentProcessingDiagnosticQueryImpl(
                        mock(DocumentProcessingRecordService.class),
                        mock(DocumentIngestionService.class),
                        mock(KnowledgeDocumentRepository.class),
                        beanFactory.getBeanProvider(AmqpAdmin.class)));

        StepVerifier.create(controller.getRabbitMQQueueStatus())
                .expectNextMatches(result -> Boolean.FALSE.equals(result.get("success"))
                        && "RabbitMQ administration is disabled".equals(result.get("error")))
                .verifyComplete();
    }
}
