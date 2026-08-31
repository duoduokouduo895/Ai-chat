package org.example.ai_agent.service.admin;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface AdminUserQuery {

    Mono<Map<String, Object>> getUsers(int page, int size, String keyword, Boolean guestOnly);
}
