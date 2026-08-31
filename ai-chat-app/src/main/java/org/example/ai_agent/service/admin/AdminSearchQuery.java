package org.example.ai_agent.service.admin;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

public interface AdminSearchQuery {

    Mono<Map<String, Object>> getOverview();

    Mono<Map<String, Object>> getHistory(int page, int size, String keyword,
                                         LocalDateTime lastTimestamp, Long lastId, boolean includeTotal);
}
