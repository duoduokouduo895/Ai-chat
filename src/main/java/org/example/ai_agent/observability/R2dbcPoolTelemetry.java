package org.example.ai_agent.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Exposes bounded pool gauges without connection URLs, SQL or borrower identifiers. */
@Slf4j
@Component
public class R2dbcPoolTelemetry {

    public R2dbcPoolTelemetry(ConnectionFactory connectionFactory, MeterRegistry meterRegistry) {
        if (!(connectionFactory instanceof ConnectionPool pool)) {
            log.warn("R2DBC 连接工厂不是 ConnectionPool，无法注册池容量指标: type={}",
                    connectionFactory.getClass().getSimpleName());
            return;
        }
        pool.getMetrics().ifPresentOrElse(metrics -> {
            Gauge.builder("aichat.r2dbc.pool.pending", metrics, value -> value.pendingAcquireSize())
                    .description("Pending R2DBC connection acquisitions")
                    .register(meterRegistry);
            Gauge.builder("aichat.r2dbc.pool.acquired", metrics, value -> value.acquiredSize())
                    .description("Acquired R2DBC connections")
                    .register(meterRegistry);
            Gauge.builder("aichat.r2dbc.pool.allocated", metrics, value -> value.allocatedSize())
                    .description("Allocated R2DBC connections")
                    .register(meterRegistry);
            Gauge.builder("aichat.r2dbc.pool.max", metrics, value -> value.getMaxAllocatedSize())
                    .description("Configured maximum R2DBC connections")
                    .register(meterRegistry);
        }, () -> log.warn("R2DBC ConnectionPool 未提供 PoolMetrics，池耗尽规则将保持证据不足"));
    }
}
