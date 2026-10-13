package org.example.ai_agent.chat.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.llm.ModelCatalogProperties;
import org.example.ai_agent.llm.ChatModelRoutingPolicy;
import org.example.ai_agent.auth.security.RequestActorResolver;
import org.example.ai_agent.chat.dto.ChatRequest;
import org.example.ai_agent.chat.service.AiChatService;
import org.example.ai_agent.common.admission.ChatAdmissionService;
import org.example.ai_agent.llm.AiModelRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
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
    private final RequestActorResolver requestActorResolver;
    private final ChatAdmissionService chatAdmissionService;
    private final ChatModelRoutingPolicy chatModelRoutingPolicy;

    public AiController(AiModelRegistry aiModelRegistry, AiChatService aiChatService,
                        RequestActorResolver requestActorResolver,
                        ChatAdmissionService chatAdmissionService,
                        ChatModelRoutingPolicy chatModelRoutingPolicy) {
        this.aiModelRegistry = aiModelRegistry;
        this.aiChatService = aiChatService;
        this.requestActorResolver = requestActorResolver;
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
        return requestActorResolver.resolve(authentication)
                .flatMapMany(actor -> {
                    Long userId = actor.userId() != null ? actor.userId() : actor.adminId();
                    if (userId == null) {
                        return Flux.error(new SecurityException("当前用户身份无效"));
                    }
                    chatRequest.setUserId(userId);
                    chatRequest.setAdminRequest(actor.admin());
                    chatRequest.setActorType(actor.admin() ? "ADMIN" : actor.guest() ? "GUEST" : "USER");
                    chatRequest.setModel(chatModelRoutingPolicy.resolve(chatRequest));

                    log.info("Chat request accepted: model={}, knowledgeBaseCount={}",
                            chatRequest.getModel(),
                            chatRequest.getKnowledgeBaseIds() != null ? chatRequest.getKnowledgeBaseIds().size() : 0);

                    return chatAdmissionService.admit(userId, actor.guest(), chatRequest)
                            .flatMapMany(lease -> Flux.usingWhen(
                                    Mono.just(lease),
                                    ignored -> aiChatService.chat(chatRequest),
                                    ignored -> lease.release(),
                                    (ignored, error) -> lease.release(),
                                    ignored -> lease.release()));
                });
    }

}

