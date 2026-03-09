package org.example.ai_agent.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgumentException(
            IllegalArgumentException ex, ServerWebExchange exchange) {

        log.error("Parameter validation failed: {}", ex.getMessage(), ex);
        return Mono.just(ResponseEntity.badRequest().body(buildError(400, ex.getMessage(), exchange)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, ServerWebExchange exchange) {

        log.error("Upload exceeded max size: {}", ex.getMessage(), ex);
        return Mono.just(ResponseEntity.badRequest().body(buildError(400, "File size exceeds the allowed limit", exchange)));
    }

    @ExceptionHandler(SecurityException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleSecurityException(
            SecurityException ex, ServerWebExchange exchange) {

        log.warn("Forbidden request: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildError(403, ex.getMessage(), exchange)));
    }

    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleRuntimeException(
            RuntimeException ex, ServerWebExchange exchange) {

        log.error("Runtime exception: {}", ex.getMessage(), ex);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(500, "Internal server error: " + ex.getMessage(), exchange)));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleException(
            Exception ex, ServerWebExchange exchange) {

        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(500, "Internal server error", exchange)));
    }

    private Map<String, Object> buildError(int code, String message, ServerWebExchange exchange) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("timestamp", System.currentTimeMillis());
        error.put("path", exchange.getRequest().getPath().value());
        return error;
    }
}
