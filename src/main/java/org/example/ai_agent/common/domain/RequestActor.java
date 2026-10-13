package org.example.ai_agent.common.domain;

import java.util.Objects;

/**
 * 进入领域 Module 后唯一允许携带的请求主体。
 * HTTP、Spring Security 和 Redis 细节只允许存在于各自的 Adapter 中。
 */
public record RequestActor(ActorType type, Long id, String username) {

    public RequestActor {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(id, "id must not be null");
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
    }

    public boolean isAdministrator() {
        return type == ActorType.ADMIN;
    }

    public enum ActorType {
        USER,
        GUEST,
        ADMIN
    }
}
