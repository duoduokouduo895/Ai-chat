package org.example.ai_agent.exception;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("minimal")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRuntimeException_shouldNotExposeInternalMessage() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test/runtime").build());

        StepVerifier.create(handler.handleRuntimeException(new RuntimeException("sensitive details"), exchange))
                .assertNext(response -> assertRuntimeErrorResponse(response, "/api/test/runtime"))
                .verifyComplete();
    }

    private void assertRuntimeErrorResponse(ResponseEntity<Map<String, Object>> response, String path) {
        assertEquals(500, response.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", response.getBody().get("errorCode"));
        assertEquals("服务器内部错误，请稍后重试", response.getBody().get("message"));
        assertEquals(path, response.getBody().get("path"));
    }
}
