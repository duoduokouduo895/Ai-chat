package org.example.ai_agent.admin.service;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface AdminUserQuery {

    Mono<Map<String, Object>> getUsers(int page, int size, String keyword, Boolean guestOnly);
}
