package org.example.ai_agent.config.performance;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

import java.time.Duration;

@Slf4j
@Configuration
public class PerformanceConfig {

    private final MeterRegistry meterRegistry;

    @Value("${performance.api.slow-threshold-ms:1000}")
    private long slowApiThresholdMs;

    public PerformanceConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Bean
    public WebFilter apiPerformanceFilter() {
        return (exchange, chain) -> {
            String uri = exchange.getRequest().getURI().getPath();
            String method = exchange.getRequest().getMethod().name();

            if (uri.startsWith("/api/")) {
                Timer.Sample sample = Timer.start(meterRegistry);
                return chain.filter(exchange)
                        .doFinally(signalType -> {
                            String statusStr = exchange.getResponse().getStatusCode() != null
                                    ? String.valueOf(exchange.getResponse().getStatusCode().value())
                                    : "UNKNOWN";

                            Timer timer = Timer.builder("api.request.duration")
                                    .tag("method", method)
                                    .tag("uri", normalizeUri(uri))
                                    .tag("status", statusStr)
                                    .publishPercentiles(0.50, 0.95, 0.99)
                                    .publishPercentileHistogram()
                                    .serviceLevelObjectives(
                                            Duration.ofMillis(100),
                                            Duration.ofMillis(300),
                                            Duration.ofSeconds(1),
                                            Duration.ofSeconds(3))
                                    .register(meterRegistry);

                            long durationNanos = sample.stop(timer);
                            Duration duration = Duration.ofNanos(durationNanos);
                            if (duration.toMillis() > slowApiThresholdMs && !uri.equals("/api/ai/chat")) {
                                log.warn("慢 API 警告: {} {} status={} signal={} 耗时 {} ms",
                                        method, uri, statusStr, signalType, duration.toMillis());
                            }
                        });
            }

            return chain.filter(exchange);
        };
    }

    private String normalizeUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return "unknown";
        }
        return uri.replaceAll("/\\d+(?=/|$)", "/{id}")
                .replaceAll("/[0-9a-fA-F-]{16,}(?=/|$)", "/{id}");
    }

    @Bean
    public Timer databaseQueryTimer() {
        return Timer.builder("database.query.duration")
                .description("数据库查询耗时")
                .register(meterRegistry);
    }

    @Bean
    public Timer cacheAccessTimer() {
        return Timer.builder("cache.access.duration")
                .description("缓存访问耗时")
                .register(meterRegistry);
    }

    @Bean
    public Timer aiResponseTimer() {
        return Timer.builder("ai.response.duration")
                .description("AI 响应耗时")
                .register(meterRegistry);
    }
}
