package org.example.ai_agent.controller.support;

import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.auth.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Resolves the authenticated request actor once at the HTTP seam.
 *
 * <p>The security filter normally places the numeric actor id in
 * {@link Authentication#getDetails()}. The repository fallbacks are retained
 * for legacy authentication sources that only provide a username.</p>
 */
@Component
public class RequestActorResolver {

    private final UserRepository userRepository;

    public RequestActorResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Mono<RequestActor> resolve(Authentication authentication) {
        if (authentication == null) {
            return Mono.error(new SecurityException("当前用户未认证"));
        }

        boolean admin = hasAuthority(authentication, "ROLE_ADMIN");
        boolean guest = hasAuthority(authentication, "ROLE_GUEST");
        Long actorId = numericDetails(authentication.getDetails());
        if (actorId != null && actorId > 0) {
            return Mono.just(admin
                    ? RequestActor.admin(actorId)
                    : RequestActor.user(actorId, guest));
        }

        if (admin) {
            // Admin endpoints may resolve the target user from a request parameter
            // or knowledge base. Keep the admin role usable for legacy tokens that
            // do not carry an id; strict JWT validation rejects such tokens earlier.
            return Mono.just(RequestActor.admin(actorId));
        }

        Mono<User> userLookup = userRepository.findByUsername(authentication.getName());
        return userLookup == null ? Mono.error(new SecurityException("当前用户不存在")) : userLookup
                .map(user -> toUserActor(user, guest))
                .switchIfEmpty(Mono.error(new SecurityException("当前用户不存在")));
    }

    public Mono<Long> requireUserId(Authentication authentication) {
        return resolve(authentication)
                .flatMap(actor -> {
                    if (actor.admin()) {
                        return Mono.error(new SecurityException("管理员账号不能执行该用户操作"));
                    }
                    if (actor.userId() == null || actor.userId() <= 0) {
                        return Mono.error(new SecurityException("当前用户身份无效"));
                    }
                    return Mono.just(actor.userId());
                });
    }

    /**
     * Preserves the existing user-scoped endpoint contract for administrator
     * Tokens. Those endpoints historically used Authentication.details as a
     * numeric owner id for both USER and ADMIN principals.
     */
    public Mono<Long> requireLegacyUserScopedActorId(Authentication authentication) {
        return resolve(authentication)
                .flatMap(actor -> {
                    Long actorId = actor.userId() != null ? actor.userId() : actor.adminId();
                    if (actorId == null || actorId <= 0) {
                        return Mono.error(new SecurityException("当前用户身份无效"));
                    }
                    return Mono.just(actorId);
                });
    }

    private RequestActor toUserActor(User user, boolean guestAuthority) {
        return RequestActor.user(user.getUserId(), guestAuthority || Boolean.TRUE.equals(user.getIsGuest()));
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private Long numericDetails(Object details) {
        if (details instanceof Number number) {
            return number.longValue();
        }
        if (details instanceof String value && !value.isBlank()) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public record RequestActor(Long userId, Long adminId, boolean guest, boolean admin) {

        static RequestActor user(Long userId, boolean guest) {
            return new RequestActor(userId, null, guest, false);
        }

        static RequestActor admin(Long adminId) {
            return new RequestActor(null, adminId, false, true);
        }
    }
}
