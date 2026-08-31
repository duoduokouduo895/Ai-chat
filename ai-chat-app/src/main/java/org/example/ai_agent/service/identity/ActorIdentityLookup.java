package org.example.ai_agent.service.identity;

import reactor.core.publisher.Mono;

/**
 * Application seam for resolving the durable identity behind an authenticated
 * principal. HTTP adapters must not access the user repository directly.
 */
public interface ActorIdentityLookup {

    Mono<ActorIdentity> findByUsername(String username);

    Mono<Long> requireExistingUserId(Long userId, String errorMessage);

    record ActorIdentity(Long userId, boolean guest) {
    }
}
