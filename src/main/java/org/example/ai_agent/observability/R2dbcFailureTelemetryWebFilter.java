package org.example.ai_agent.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/** Emits one privacy-safe operation event when a request fails while acquiring a pooled R2DBC connection. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class R2dbcFailureTelemetryWebFilter implements WebFilter {

    private static final Logger OPERATIONS = LoggerFactory.getLogger("aichat.operations");
    private final ObjectMapper objectMapper;

    public R2dbcFailureTelemetryWebFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange).doOnError(error -> {
            if (isPoolAcquireTimeout(error)) {
                OPERATIONS.warn(eventJson());
            }
        });
    }

    boolean isPoolAcquireTimeout(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            String type = current.getClass().getName();
            if (type.contains("PoolAcquireTimeout") || type.contains("R2dbcTimeout")) {
                return true;
            }
        }
        return false;
    }

    String eventJson() {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "r2dbc_pool_acquire");
        event.put("result", "timeout");
        event.put("errorType", "POOL_ACQUIRE_TIMEOUT");
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            return "{\"event\":\"r2dbc_pool_acquire\",\"result\":\"logging_error\"}";
        }
    }
}
