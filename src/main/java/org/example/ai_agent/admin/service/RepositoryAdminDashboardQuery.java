package org.example.ai_agent.admin.service;

import org.example.ai_agent.auth.repository.UserRepository;
import org.example.ai_agent.chat.repository.ConversationRepository;
import org.example.ai_agent.knowledge.repository.KnowledgeBaseRepository;
import org.example.ai_agent.llm.AiModelRegistry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class RepositoryAdminDashboardQuery implements AdminDashboardQuery {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final AiModelRegistry aiModelRegistry;

    public RepositoryAdminDashboardQuery(UserRepository userRepository,
                                         ConversationRepository conversationRepository,
                                         KnowledgeBaseRepository knowledgeBaseRepository,
                                         AiModelRegistry aiModelRegistry) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.aiModelRegistry = aiModelRegistry;
    }

    @Override
    public Mono<Map<String, Long>> getDashboardStats() {
        return Mono.zip(
                userRepository.count(),
                Mono.fromSupplier(() -> (long) aiModelRegistry.getModelMetadata().size()),
                conversationRepository.count(),
                knowledgeBaseRepository.countByStatus("ACTIVE")
        ).map(tuple -> {
            Map<String, Long> stats = new HashMap<>();
            stats.put("userCount", tuple.getT1());
            stats.put("modelCount", tuple.getT2());
            stats.put("conversationCount", tuple.getT3());
            stats.put("knowledgeBaseCount", tuple.getT4());
            return stats;
        });
    }
}
