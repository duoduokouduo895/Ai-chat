package org.example.ai_agent.admin.service;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface AdminDashboardQuery {

    Mono<Map<String, Long>> getDashboardStats();
}
