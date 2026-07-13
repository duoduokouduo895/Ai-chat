package org.example.ai_agent.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import org.example.ai_agent.service.impl.chat.ChatStreamClientException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChatStreamTelemetryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ChatStreamTelemetry telemetry = new ChatStreamTelemetry(registry, objectMapper);

    @Test
    void recordsSuccessTtftAndBoundedTagsForEverySubscription() {
        Flux<ServerSentEvent<String>> observed = telemetry.observe(Flux.just(
                ServerSentEvent.<String>builder().event("status").data("connecting").build(),
                ServerSentEvent.<String>builder().event("message").data("private answer").build()), true);

        StepVerifier.create(observed).expectNextCount(2).verifyComplete();
        StepVerifier.create(observed).expectNextCount(2).verifyComplete();

        assertEquals(2.0, registry.get("aichat.chat.requests")
                .tags("outcome", "success", "rag", "true").counter().count());
        assertEquals(2, registry.get("aichat.chat.ttft").tag("rag", "true").timer().count());
        assertEquals(0.0, registry.get("aichat.chat.streams.active").gauge().value());
        registry.getMeters().forEach(meter -> meter.getId().getTags().forEach(tag -> {
            assertFalse(tag.getValue().contains("private answer"));
            assertFalse(tag.getKey().matches("(?i).*user|.*conversation|.*prompt"));
        }));
    }

    @Test
    void recordsCancellationAndReleasesActiveGauge() {
        StepVerifier.create(telemetry.observe(Flux.never(), false))
                .thenAwait(Duration.ofMillis(5))
                .thenCancel()
                .verify();

        assertEquals(1.0, registry.get("aichat.chat.requests")
                .tags("outcome", "cancelled", "rag", "false").counter().count());
        assertEquals(0.0, registry.get("aichat.chat.streams.active").gauge().value());
    }

    @Test
    void classifiesTimeoutWithoutUsingExceptionMessagesAsTags() {
        StepVerifier.create(telemetry.observe(Flux.error(ChatStreamClientException.modelTimeout()), false))
                .expectError(ChatStreamClientException.class)
                .verify();

        assertEquals(1.0, registry.get("aichat.chat.requests")
                .tags("outcome", "timeout", "rag", "false").counter().count());
        assertEquals(0.0, registry.get("aichat.chat.streams.active").gauge().value());
    }

    @Test
    void classifiesInternalErrorWithoutUsingItsMessageAsATag() {
        StepVerifier.create(telemetry.observe(Flux.error(new IllegalStateException("private failure detail")), true))
                .expectError(IllegalStateException.class)
                .verify();

        assertEquals(1.0, registry.get("aichat.chat.requests")
                .tags("outcome", "error", "rag", "true").counter().count());
        registry.getMeters().forEach(meter -> meter.getId().getTags().forEach(tag ->
                assertFalse(tag.getValue().contains("private failure detail"))));
    }

    @Test
    void concurrentSubscriptionsDoNotLeakTheActiveGauge() {
        Mono<Void> subscriptions = Flux.range(0, 64)
                .flatMap(ignored -> telemetry.observe(Flux.just(
                        ServerSentEvent.<String>builder().event("message").data("chunk").build()), false).then(), 16)
                .then();

        StepVerifier.create(subscriptions).verifyComplete();

        assertEquals(64.0, registry.get("aichat.chat.requests")
                .tags("outcome", "success", "rag", "false").counter().count());
        assertEquals(0.0, registry.get("aichat.chat.streams.active").gauge().value());
    }

    @Test
    void operationalJsonContainsOnlyControlledFields() throws Exception {
        SpanContext spanContext = SpanContext.create(
                "0123456789abcdef0123456789abcdef",
                "0123456789abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault());
        String json;
        try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
            json = telemetry.operationalEventJson("error", 17, "INTERNAL_ERROR");
        }
        JsonNode root = objectMapper.readTree(json);

        assertEquals(6, root.size());
        assertEquals("ai_chat_stream_finished", root.get("event").asText());
        assertEquals("error", root.get("result").asText());
        assertEquals("0123456789abcdef0123456789abcdef", root.get("traceId").asText());
        assertEquals("0123456789abcdef", root.get("spanId").asText());
        assertFalse(json.matches("(?is).*(prompt|answer|userId|conversationId|token|authorization).*"));
    }
}
