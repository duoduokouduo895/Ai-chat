package org.example.ai_agent.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ChatStreamTelemetry {

    static final String REACTOR_TRACE_ID = ChatStreamTelemetry.class.getName() + ".traceId";
    static final String REACTOR_SPAN_ID = ChatStreamTelemetry.class.getName() + ".spanId";
    private static final Logger OPERATIONS = LoggerFactory.getLogger("aichat.operations");
    private static final String UNKNOWN_TRACE_ID = "00000000000000000000000000000000";
    private static final String UNKNOWN_SPAN_ID = "0000000000000000";

    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final AtomicInteger activeStreams = new AtomicInteger();

    public ChatStreamTelemetry(MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        Gauge.builder("aichat.chat.streams.active", activeStreams, AtomicInteger::get)
                .description("Active AI chat SSE streams")
                .register(meterRegistry);
    }

    public Flux<ServerSentEvent<String>> observe(Flux<ServerSentEvent<String>> source, boolean ragMode) {
        return Flux.deferContextual(context -> {
            TraceIdentifiers identifiers = context.hasKey(REACTOR_TRACE_ID)
                    ? new TraceIdentifiers(context.get(REACTOR_TRACE_ID),
                            context.getOrDefault(REACTOR_SPAN_ID, UNKNOWN_SPAN_ID))
                    : currentTraceIdentifiers();
            StreamObservation observation = new StreamObservation(ragMode, identifiers);
            activeStreams.incrementAndGet();
            return source
                    .doOnNext(observation::onEvent)
                    .doOnError(observation.error::set)
                    .doFinally(observation::finish);
        });
    }

    String operationalEventJson(String outcome, long durationMs, String errorType) {
        TraceIdentifiers identifiers = currentTraceIdentifiers();
        return operationalEventJson(outcome, durationMs, errorType,
                identifiers.traceId(), identifiers.spanId());
    }

    private String operationalEventJson(String outcome, long durationMs, String errorType,
                                        String traceId, String spanId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "ai_chat_stream_finished");
        event.put("result", outcome);
        event.put("durationMs", Math.max(0, durationMs));
        event.put("errorType", errorType);
        event.put("traceId", traceId);
        event.put("spanId", spanId);
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            return "{\"event\":\"ai_chat_stream_finished\",\"result\":\"logging_error\"}";
        }
    }

    private String safeMdc(String key, String fallback) {
        String value = MDC.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private TraceIdentifiers currentTraceIdentifiers() {
        SpanContext spanContext = Span.current().getSpanContext();
        if (spanContext.isValid()) {
            return new TraceIdentifiers(spanContext.getTraceId(), spanContext.getSpanId());
        }
        return new TraceIdentifiers(
                safeMdc("trace_id", UNKNOWN_TRACE_ID),
                safeMdc("span_id", UNKNOWN_SPAN_ID));
    }

    private final class StreamObservation {
        private final String rag;
        private final long startedNanos = System.nanoTime();
        private final AtomicBoolean firstMessage = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private final TraceIdentifiers traceIdentifiers;

        private StreamObservation(boolean ragMode, TraceIdentifiers traceIdentifiers) {
            this.rag = Boolean.toString(ragMode);
            this.traceIdentifiers = traceIdentifiers;
        }

        private void onEvent(ServerSentEvent<String> event) {
            if (event != null && "message".equals(event.event()) && firstMessage.compareAndSet(false, true)) {
                Timer.builder("aichat.chat.ttft")
                        .description("Time to first non-empty chat message event")
                        .tag("rag", rag)
                        .publishPercentileHistogram()
                        .serviceLevelObjectives(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(3))
                        .register(meterRegistry)
                        .record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS);
            }
        }

        private void finish(SignalType signalType) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            activeStreams.decrementAndGet();
            Throwable failure = error.get();
            String outcome = classify(signalType, failure);
            long durationNanos = Math.max(0, System.nanoTime() - startedNanos);

            Counter.builder("aichat.chat.requests")
                    .description("Completed AI chat SSE subscriptions")
                    .tag("outcome", outcome)
                    .tag("rag", rag)
                    .register(meterRegistry)
                    .increment();
            Timer.builder("aichat.chat.stream.duration")
                    .description("AI chat SSE subscription duration")
                    .tag("outcome", outcome)
                    .tag("rag", rag)
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);

            OPERATIONS.info(operationalEventJson(outcome, TimeUnit.NANOSECONDS.toMillis(durationNanos),
                    failure == null ? "none" : safeErrorType(failure),
                    traceIdentifiers.traceId(), traceIdentifiers.spanId()));
        }

        private String classify(SignalType signalType, Throwable failure) {
            if (signalType == SignalType.CANCEL) {
                return "cancelled";
            }
            if (failure instanceof org.example.ai_agent.service.impl.chat.ChatStreamClientException clientException
                    && "CHAT_TIMEOUT".equals(clientException.getErrorCode())) {
                return "timeout";
            }
            return failure == null ? "success" : "error";
        }

        private String safeErrorType(Throwable failure) {
            if (failure instanceof org.example.ai_agent.service.impl.chat.ChatStreamClientException clientException) {
                return clientException.getErrorCode();
            }
            return "INTERNAL_ERROR";
        }
    }

    private record TraceIdentifiers(String traceId, String spanId) {
    }
}
