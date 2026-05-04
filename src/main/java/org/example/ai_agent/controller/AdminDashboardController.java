package org.example.ai_agent.controller;

import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.repository.chat.ConversationRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeBaseRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@CrossOrigin(origins = {"https://dduo.chat", "http://dduo.chat", "http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
public class AdminDashboardController {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public AdminDashboardController(UserRepository userRepository,
                                    ConversationRepository conversationRepository,
                                    KnowledgeBaseRepository knowledgeBaseRepository) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    @GetMapping("/stats")
    public Mono<Map<String, Long>> getDashboardStats() {
        return Mono.zip(
                userRepository.count(),
                Mono.just(3L),
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


