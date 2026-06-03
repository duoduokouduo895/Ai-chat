package org.example.ai_agent.controller;

import org.example.ai_agent.config.ai.ModelCatalogProperties;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.repository.chat.ConversationRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeBaseRepository;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private AiModelRegistry aiModelRegistry;

    @Test
    void getDashboardStats_shouldUseRuntimeModelRegistryCount() {
        when(userRepository.count()).thenReturn(Mono.just(11L));
        when(conversationRepository.count()).thenReturn(Mono.just(22L));
        when(knowledgeBaseRepository.countByStatus("ACTIVE")).thenReturn(Mono.just(33L));
        when(aiModelRegistry.getModelMetadata())
                .thenReturn(List.of(new ModelCatalogProperties.Metadata(), new ModelCatalogProperties.Metadata()));

        AdminDashboardController controller = new AdminDashboardController(
                userRepository, conversationRepository, knowledgeBaseRepository, aiModelRegistry);

        StepVerifier.create(controller.getDashboardStats())
                .assertNext(stats -> {
                    org.junit.jupiter.api.Assertions.assertEquals(11L, stats.get("userCount"));
                    org.junit.jupiter.api.Assertions.assertEquals(2L, stats.get("modelCount"));
                    org.junit.jupiter.api.Assertions.assertEquals(22L, stats.get("conversationCount"));
                    org.junit.jupiter.api.Assertions.assertEquals(33L, stats.get("knowledgeBaseCount"));
                })
                .verifyComplete();
    }
}
