package org.example.ai_agent.admin.controller;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class AdminPerformanceControllerTest {

    @Test
    void getApiPerformance_shouldExposeApiAndSearchQueueMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Timer timer = Timer.builder("api.request.duration")
                .tag("method", "GET")
                .tag("uri", "/api/conversations/recent")
                .tag("status", "200")
                .publishPercentiles(0.95)
                .register(meterRegistry);
        timer.record(Duration.ofMillis(120));
        meterRegistry.counter("search.index.queue.enqueued").increment(2);
        AtomicInteger pending = new AtomicInteger(1);
        Gauge.builder("search.index.queue.pending", pending, AtomicInteger::get)
                .register(meterRegistry);

        Map<String, Object> payload = new AdminPerformanceController(meterRegistry).getApiPerformance(10);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> queue = (Map<String, Object>) payload.get("searchIndexQueue");

        org.junit.jupiter.api.Assertions.assertEquals(1, items.size());
        org.junit.jupiter.api.Assertions.assertEquals("/api/conversations/recent", items.get(0).get("uri"));
        org.junit.jupiter.api.Assertions.assertEquals(1L, items.get(0).get("count"));
        org.junit.jupiter.api.Assertions.assertEquals(1.0, queue.get("pending"));
        org.junit.jupiter.api.Assertions.assertEquals(2.0, queue.get("enqueued"));
    }
}
