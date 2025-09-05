package org.example.ai_agent.config.performance;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;
import java.time.Duration;

/**
 * 性能监控配置
 * 提供API响应时间监控和性能指标收集
 */
@Configuration
public class PerformanceConfig {

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * API响应时间监控过滤器
     */
    @Bean
    public WebFilter apiPerformanceFilter() {
        return (exchange, chain) -> {
            String uri = exchange.getRequest().getURI().getPath();
            String method = exchange.getRequest().getMethod().name();

            // 只监控API接口
            if (uri.startsWith("/api/")) {
                Timer.Sample sample = Timer.start(meterRegistry);
                return chain.filter(exchange)
                    .doFinally(signalType -> {
                        Integer status = exchange.getResponse().getRawStatusCode();
                        String statusStr = status != null ? String.valueOf(status) : "UNKNOWN";

                        Timer timer = Timer.builder("api.request.duration")
                                .tag("method", method)
                                .tag("uri", uri)
                                .tag("status", statusStr)
                                .register(meterRegistry);

                        long durationNanos = sample.stop(timer);

                        // 记录慢查询（超过1秒），但排除流式API，因为它本身就是长连接
                        Duration duration = Duration.ofNanos(durationNanos);
                        if (duration.toMillis() > 1000 && !uri.equals("/api/ai/chat")) {
                            System.out.println(String.format("慢API警告: %s %s 耗时 %d ms",
                                method, uri, duration.toMillis()));
                        }
                    });
            } else {
                return chain.filter(exchange);
            }
        };
    }

    /**
     * 数据库连接池监控
     */
    @Bean
    public Timer databaseQueryTimer() {
        return Timer.builder("database.query.duration")
                .description("数据库查询耗时")
                .register(meterRegistry);
    }

    /**
     * 缓存命中率监控
     */
    @Bean
    public Timer cacheAccessTimer() {
        return Timer.builder("cache.access.duration")
                .description("缓存访问耗时")
                .register(meterRegistry);
    }

    /**
     * AI响应时间监控
     */
    @Bean
    public Timer aiResponseTimer() {
        return Timer.builder("ai.response.duration")
                .description("AI响应耗时")
                .register(meterRegistry);
    }
}
