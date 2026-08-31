package org.example.ai_agent.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.PoolMetrics;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class R2dbcPoolTelemetryTest {

    @Test
    void exposesOnlyBoundedCapacityGauges() {
        var pool = mock(ConnectionPool.class);
        var metrics = mock(PoolMetrics.class);
        when(pool.getMetrics()).thenReturn(Optional.of(metrics));
        when(metrics.pendingAcquireSize()).thenReturn(2);
        when(metrics.acquiredSize()).thenReturn(4);
        when(metrics.allocatedSize()).thenReturn(6);
        when(metrics.getMaxAllocatedSize()).thenReturn(20);
        var registry = new SimpleMeterRegistry();

        new R2dbcPoolTelemetry(pool, registry);

        assertThat(registry.get("aichat.r2dbc.pool.pending").gauge().value()).isEqualTo(2);
        assertThat(registry.get("aichat.r2dbc.pool.acquired").gauge().value()).isEqualTo(4);
        assertThat(registry.get("aichat.r2dbc.pool.allocated").gauge().value()).isEqualTo(6);
        assertThat(registry.get("aichat.r2dbc.pool.max").gauge().value()).isEqualTo(20);
    }
}
