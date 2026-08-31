package org.example.ai_agent.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeSearchTelemetryTest {

    @Test
    void recordsOnlyBoundedOutcomeAndNeverSerializesSearchContent() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new KnowledgeSearchTelemetry(registry, new ObjectMapper());

        StepVerifier.create(telemetry.observe(Flux.error(new IllegalStateException("secret query and token"))))
                .expectError(IllegalStateException.class).verify();

        assertThat(registry.get("aichat.search.requests").tag("outcome", "error").counter().count()).isEqualTo(1);
        assertThat(telemetry.eventJson("error", new IllegalStateException("secret query and token")))
                .contains("knowledge_search", "SEARCH_BACKEND_ERROR")
                .doesNotContain("secret query", "token", "IllegalStateException");
    }
}
