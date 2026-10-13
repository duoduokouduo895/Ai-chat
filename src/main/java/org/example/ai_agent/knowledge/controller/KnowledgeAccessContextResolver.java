package org.example.ai_agent.knowledge.controller;

import org.example.ai_agent.knowledge.service.KnowledgeBaseService;
import org.example.ai_agent.auth.service.ActorIdentityLookup;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import org.example.ai_agent.auth.security.RequestActorResolver;

@Component
public class KnowledgeAccessContextResolver {

    private final KnowledgeBaseService knowledgeBaseService;
    private final ActorIdentityLookup actorIdentityLookup;
    private final RequestActorResolver requestActorResolver;

    public KnowledgeAccessContextResolver(KnowledgeBaseService knowledgeBaseService,
                                          ActorIdentityLookup actorIdentityLookup,
                                          RequestActorResolver requestActorResolver) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.actorIdentityLookup = actorIdentityLookup;
        this.requestActorResolver = requestActorResolver;
    }

    public Mono<AccessContext> resolve(Authentication authentication) {
        return requestActorResolver.resolve(authentication)
                .map(actor -> new AccessContext(actor.admin() ? actor.adminId() : actor.userId(), actor.admin()));
    }

    public Mono<AccessContext> requireUser(Authentication authentication) {
        return resolve(authentication)
                .flatMap(access -> {
                    if (access.isAdmin()) {
                        return Mono.error(new SecurityException("管理员账号不能执行该操作"));
                    }
                    if (access.userId() == null || access.userId() <= 0) {
                        return Mono.error(new SecurityException("无法识别当前用户"));
                    }
                    return Mono.just(access);
                })
                .onErrorMap(error -> error instanceof SecurityException
                                && "当前用户不存在".equals(error.getMessage()),
                        error -> new SecurityException("无法识别当前用户"));
    }

    public Mono<Void> requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null
                || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            return Mono.error(new SecurityException("当前用户无权访问管理员资源"));
        }
        return resolve(authentication)
                .flatMap(access -> access.isAdmin()
                        ? Mono.<Void>empty()
                        : Mono.error(new SecurityException("当前用户无权访问管理员资源")));
    }

    public Mono<Long> resolveAdminUploaderId(Long knowledgeBaseId, Long uploaderId) {
        if (uploaderId != null) {
            return requireExistingUserId(uploaderId, "上传用户不存在");
        }

        return knowledgeBaseService.getKnowledgeBase(knowledgeBaseId)
                .flatMap(knowledgeBase -> requireExistingUserId(
                        knowledgeBase.getCreatorId(), "知识库归属用户不存在"));
    }

    public Mono<Long> requireExistingUserId(Long userId, String errorMessage) {
        if (userId == null || userId <= 0) {
            return Mono.error(new IllegalArgumentException(errorMessage));
        }

        return actorIdentityLookup.requireExistingUserId(userId, errorMessage);
    }

    public Long parseOptionalLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    public record AccessContext(Long userId, boolean isAdmin) {
    }
}
