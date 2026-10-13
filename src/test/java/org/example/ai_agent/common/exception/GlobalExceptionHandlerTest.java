package org.example.ai_agent.common.exception;

import org.example.ai_agent.common.admission.ChatAdmissionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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

    @Test
    void handleChatAdmissionException_shouldKeepForbiddenStatusAndReason() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/chat").build());

        StepVerifier.create(handler.handleResponseStatusException(
                        new ChatAdmissionException(HttpStatus.FORBIDDEN, "游客仅可使用默认模型"), exchange))
                .assertNext(response -> {
                    assertEquals(403, response.getStatusCode().value());
                    assertEquals("FORBIDDEN", response.getBody().get("errorCode"));
                    assertEquals("游客仅可使用默认模型", response.getBody().get("message"));
                    assertEquals("/api/ai/chat", response.getBody().get("path"));
                })
                .verifyComplete();
    }

    @Test
    void handleChatAdmissionException_shouldKeepTooManyRequestsStatusAndReason() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/ai/chat").build());

        StepVerifier.create(handler.handleResponseStatusException(
                        new ChatAdmissionException(HttpStatus.TOO_MANY_REQUESTS, "当前账户已有进行中的对话，请等待完成后再试"),
                        exchange))
                .assertNext(response -> {
                    assertEquals(429, response.getStatusCode().value());
                    assertEquals("TOO_MANY_REQUESTS", response.getBody().get("errorCode"));
                    assertEquals("当前账户已有进行中的对话，请等待完成后再试", response.getBody().get("message"));
                })
                .verifyComplete();
    }

    private void assertRuntimeErrorResponse(ResponseEntity<Map<String, Object>> response, String path) {
        assertEquals(500, response.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", response.getBody().get("errorCode"));
        assertEquals("服务器内部错误，请稍后重试", response.getBody().get("message"));
        assertEquals(path, response.getBody().get("path"));
    }
}
