package org.example.ai_agent.service.admin;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface AdminDashboardQuery {

    Mono<Map<String, Long>> getDashboardStats();
}
