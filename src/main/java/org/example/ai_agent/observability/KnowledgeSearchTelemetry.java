package org.example.ai_agent.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class KnowledgeSearchTelemetry {

    private static final Logger OPERATIONS = LoggerFactory.getLogger("aichat.operations");
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public KnowledgeSearchTelemetry(MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        for (String outcome : new String[]{"success", "error", "cancelled"}) {
            Counter.builder("aichat.search.requests").tag("outcome", outcome).register(meterRegistry);
        }
    }

    public <T> Flux<T> observe(Flux<T> source) {
        return Flux.defer(() -> {
            AtomicBoolean finished = new AtomicBoolean();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            return source.doOnError(failure::set).doFinally(signal -> {
                if (!finished.compareAndSet(false, true)) {
                    return;
                }
                String result = signal == reactor.core.publisher.SignalType.CANCEL ? "cancelled"
                        : failure.get() == null ? "success" : "error";
                meterRegistry.counter("aichat.search.requests", "outcome", result).increment();
                OPERATIONS.info(eventJson(result, failure.get()));
            });
        });
    }

    String eventJson(String result, Throwable failure) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "knowledge_search");
        event.put("result", result);
        event.put("errorType", failure == null ? "none" : "SEARCH_BACKEND_ERROR");
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            return "{\"event\":\"knowledge_search\",\"result\":\"logging_error\"}";
        }
    }
}
