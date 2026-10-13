package org.example.ai_agent.common.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class R2dbcFailureTelemetryWebFilterTest {

    @Test
    void recognizesNestedPoolTimeoutWithoutSerializingRequestOrException() {
        var filter = new R2dbcFailureTelemetryWebFilter(new ObjectMapper());
        var failure = new RuntimeException("contains password", new TestPoolAcquireTimeoutException("secret SQL"));

        assertThat(filter.isPoolAcquireTimeout(failure)).isTrue();
        assertThat(filter.eventJson()).contains("r2dbc_pool_acquire", "POOL_ACQUIRE_TIMEOUT")
                .doesNotContain("password", "secret SQL", "TestPoolAcquireTimeoutException");
    }

    private static final class TestPoolAcquireTimeoutException extends RuntimeException {
        private TestPoolAcquireTimeoutException(String message) {
            super(message);
        }
    }
}
