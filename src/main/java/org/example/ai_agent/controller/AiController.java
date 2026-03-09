package org.example.ai_agent.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.ai.AiModelProperties;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.AiChatService;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = {"http://localhost:3000", "http://dduo.chat", "https://dduo.chat"}, allowCredentials = "true")
public class AiController {

    private final AiModelRegistry aiModelRegistry;
    private final AiChatService aiChatService;
    private final UserRepository userRepository;

    public AiController(AiModelRegistry aiModelRegistry, AiChatService aiChatService, UserRepository userRepository) {
        this.aiModelRegistry = aiModelRegistry;
        this.aiChatService = aiChatService;
        this.userRepository = userRepository;
    }

    @GetMapping("/models")
    public Mono<List<AiModelProperties.Metadata>> getAvailableModels() {
        return Mono.just(aiModelRegistry.getModelMetadata());
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequest chatRequest, Authentication authentication) {
        return resolveCurrentUserId(authentication)
                .flatMapMany(userId -> {
                    chatRequest.setUserId(userId);

                    log.info("Chat request from user {}, model={}, knowledgeBaseCount={}",
                            userId,
                            chatRequest.getModel(),
                            chatRequest.getKnowledgeBaseIds() != null ? chatRequest.getKnowledgeBaseIds().size() : 0);

                    return aiChatService.chat(chatRequest);
                });
    }

    private Mono<Long> resolveCurrentUserId(Authentication authentication) {
        if (authentication == null) {
            return Mono.error(new SecurityException("Current user is not authenticated"));
        }

        Object details = authentication.getDetails();
        if (details instanceof Long userId) {
            return Mono.just(userId);
        }
        if (details instanceof Number number) {
            return Mono.just(number.longValue());
        }
        if (details instanceof String value && !value.isBlank()) {
            try {
                return Mono.just(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                log.warn("Authentication details are not a numeric user id: {}", value);
            }
        }

        return userRepository.findByUsername(authentication.getName())
                .map(user -> user.getUserId())
                .switchIfEmpty(Mono.error(new SecurityException("Current user does not exist")));
    }
}
