package org.example.ai_agent.controller.support;

import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class KnowledgeAccessContextResolver {

    private final KnowledgeBaseService knowledgeBaseService;
    private final UserRepository userRepository;

    public KnowledgeAccessContextResolver(KnowledgeBaseService knowledgeBaseService,
                                          UserRepository userRepository) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.userRepository = userRepository;
    }

    public Mono<AccessContext> resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Mono.error(new SecurityException("当前用户未认证"));
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        Long actorId = extractActorId(authentication);
        return Mono.just(new AccessContext(actorId, isAdmin));
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
                });
    }

    public Mono<Void> requireAdmin(Authentication authentication) {
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

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(errorMessage)))
                .map(user -> user.getUserId());
    }

    public Long parseOptionalLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    private Long extractActorId(Authentication authentication) {
        Object details = authentication.getDetails();
        if (details instanceof Long id) {
            return id;
        }
        if (details instanceof Number number) {
            return number.longValue();
        }
        if (details instanceof String value && !value.isBlank()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public record AccessContext(Long userId, boolean isAdmin) {
    }
}
