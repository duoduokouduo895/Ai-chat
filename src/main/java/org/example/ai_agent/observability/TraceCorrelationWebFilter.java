package org.example.ai_agent.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TraceCorrelationWebFilter implements WebFilter {

    private static final Pattern TRACEPARENT = Pattern.compile(
            "^[0-9a-fA-F]{2}-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-[0-9a-fA-F]{2}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        TraceIdentifiers identifiers = currentOrIncoming(exchange);
        if (identifiers == null) {
            return chain.filter(exchange);
        }
        return chain.filter(exchange).contextWrite(context -> context
                .put(ChatStreamTelemetry.REACTOR_TRACE_ID, identifiers.traceId())
                .put(ChatStreamTelemetry.REACTOR_SPAN_ID, identifiers.spanId()));
    }

    private TraceIdentifiers currentOrIncoming(ServerWebExchange exchange) {
        SpanContext current = Span.current().getSpanContext();
        if (current.isValid()) {
            return new TraceIdentifiers(current.getTraceId(), current.getSpanId());
        }

        String traceparent = exchange.getRequest().getHeaders().getFirst("traceparent");
        if (traceparent == null) {
            return null;
        }
        Matcher matcher = TRACEPARENT.matcher(traceparent.trim());
        if (!matcher.matches()) {
            return null;
        }
        String traceId = matcher.group(1).toLowerCase(Locale.ROOT);
        String spanId = matcher.group(2).toLowerCase(Locale.ROOT);
        if (traceId.chars().allMatch(character -> character == '0')
                || spanId.chars().allMatch(character -> character == '0')) {
            return null;
        }
        return new TraceIdentifiers(traceId, spanId);
    }

    private record TraceIdentifiers(String traceId, String spanId) {
    }
}
