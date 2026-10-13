package org.example.ai_agent.admin.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 管理端性能指标摘要。
 *
 * Actuator 保留完整指标，这里提供适合管理页和排障直接消费的热点视图。
 */
@RestController
@RequestMapping("/api/admin/performance")
public class AdminPerformanceController {

    private final MeterRegistry meterRegistry;

    public AdminPerformanceController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/apis")
    public Map<String, Object> getApiPerformance(@RequestParam(defaultValue = "20") Integer limit) {
        int finalLimit = limit != null && limit > 0 && limit <= 100 ? limit : 20;

        List<Map<String, Object>> items = meterRegistry.find("api.request.duration")
                .timers()
                .stream()
                .map(this::toApiSnapshot)
                .sorted(Comparator.comparingDouble(item -> -((Number) item.get("p95Ms")).doubleValue()))
                .limit(finalLimit)
                .toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("items", items);
        payload.put("searchIndexQueue", searchIndexQueueSnapshot());
        return payload;
    }

    private Map<String, Object> toApiSnapshot(Timer timer) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("method", tagValue(timer, "method"));
        snapshot.put("uri", tagValue(timer, "uri"));
        snapshot.put("status", tagValue(timer, "status"));
        snapshot.put("count", timer.count());
        snapshot.put("totalMs", timer.totalTime(TimeUnit.MILLISECONDS));
        snapshot.put("maxMs", timer.max(TimeUnit.MILLISECONDS));
        snapshot.put("p95Ms", percentileMs(timer, 0.95));
        return snapshot;
    }

    private Map<String, Object> searchIndexQueueSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("pending", gaugeValue("search.index.queue.pending"));
        snapshot.put("enqueued", counterValue("search.index.queue.enqueued"));
        snapshot.put("coalesced", counterValue("search.index.queue.coalesced"));
        snapshot.put("deferred", counterValue("search.index.queue.deferred"));
        snapshot.put("processed", counterValue("search.index.queue.processed"));
        snapshot.put("failed", counterValue("search.index.queue.failed"));
        return snapshot;
    }

    private double percentileMs(Timer timer, double percentile) {
        return Arrays.stream(timer.takeSnapshot().percentileValues())
                .filter(value -> Math.abs(value.percentile() - percentile) < 0.0001)
                .findFirst()
                .map(value -> value.value(TimeUnit.MILLISECONDS))
                .orElse(0.0);
    }

    private String tagValue(Timer timer, String tagName) {
        String value = timer.getId().getTag(tagName);
        return value != null ? value : "unknown";
    }

    private double gaugeValue(String name) {
        Gauge gauge = meterRegistry.find(name).gauge();
        return gauge != null ? gauge.value() : 0.0;
    }

    private double counterValue(String name) {
        Counter counter = meterRegistry.find(name).counter();
        return counter != null ? counter.count() : 0.0;
    }
}
