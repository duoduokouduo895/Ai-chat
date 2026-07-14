package org.example.ai_agent.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.ai.ModelCatalogProperties;
import org.example.ai_agent.config.ai.ChatModelRoutingPolicy;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.AiChatService;
import org.example.ai_agent.service.admission.ChatAdmissionService;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
public class AiController {

    private final AiModelRegistry aiModelRegistry;
    private final AiChatService aiChatService;
    private final UserRepository userRepository;
    private final ChatAdmissionService chatAdmissionService;
    private final ChatModelRoutingPolicy chatModelRoutingPolicy;

    public AiController(AiModelRegistry aiModelRegistry, AiChatService aiChatService, UserRepository userRepository,
                        ChatAdmissionService chatAdmissionService,
                        ChatModelRoutingPolicy chatModelRoutingPolicy) {
        this.aiModelRegistry = aiModelRegistry;
        this.aiChatService = aiChatService;
        this.userRepository = userRepository;
        this.chatAdmissionService = chatAdmissionService;
        this.chatModelRoutingPolicy = chatModelRoutingPolicy;
    }

    @GetMapping("/models")
    public Mono<List<ModelCatalogProperties.Metadata>> getAvailableModels() {
        return Mono.just(aiModelRegistry.getModelMetadata());
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ChatRequest chatRequest,
                                                    Authentication authentication) {
        return resolveCurrentUserId(authentication)
                .flatMapMany(userId -> {
                    chatRequest.setUserId(userId);
                    chatRequest.setAdminRequest(isAdmin(authentication));
                    chatRequest.setModel(chatModelRoutingPolicy.resolve(chatRequest));

                    log.info("Chat request accepted: model={}, knowledgeBaseCount={}",
                            chatRequest.getModel(),
                            chatRequest.getKnowledgeBaseIds() != null ? chatRequest.getKnowledgeBaseIds().size() : 0);

                    return chatAdmissionService.admit(userId, isGuest(authentication), chatRequest)
                            .flatMapMany(lease -> Flux.usingWhen(
                                    Mono.just(lease),
                                    ignored -> aiChatService.chat(chatRequest),
                                    ignored -> lease.release(),
                                    (ignored, error) -> lease.release(),
                                    ignored -> lease.release()));
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
                log.warn("Authentication details are not a numeric user id: detailType={}",
                        details.getClass().getSimpleName());
            }
        }

        return userRepository.findByUsername(authentication.getName())
                .map(user -> user.getUserId())
                .switchIfEmpty(Mono.error(new SecurityException("Current user does not exist")));
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private boolean isGuest(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_GUEST"::equals);
    }
}


