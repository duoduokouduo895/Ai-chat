package org.example.ai_agent.observability;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TraceCorrelationWebFilterTest {

    private final TraceCorrelationWebFilter filter = new TraceCorrelationWebFilter();

    @Test
    void propagatesValidatedTraceparentThroughReactorContext() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/ai/chat")
                .header("traceparent", "00-0123456789ABCDEF0123456789ABCDEF-0123456789ABCDEF-01")
                .build());

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.deferContextual(context -> {
                    assertEquals("0123456789abcdef0123456789abcdef",
                            context.get(ChatStreamTelemetry.REACTOR_TRACE_ID));
                    assertEquals("0123456789abcdef", context.get(ChatStreamTelemetry.REACTOR_SPAN_ID));
                    return Mono.empty();
                })))
                .verifyComplete();
    }

    @Test
    void rejectsMalformedOrAllZeroTraceparent() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/ai/chat")
                .header("traceparent", "00-00000000000000000000000000000000-0000000000000000-01")
                .build());

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.deferContextual(context -> {
                    assertFalse(context.hasKey(ChatStreamTelemetry.REACTOR_TRACE_ID));
                    return Mono.empty();
                })))
                .verifyComplete();
    }
}
