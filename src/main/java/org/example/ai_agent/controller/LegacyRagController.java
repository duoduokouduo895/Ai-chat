package org.example.ai_agent.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 已停用 RAG 接口的兼容 Adapter。
 *
 * <p>保留旧 URL 和 410 响应，避免已发布客户端得到路由不存在；不再进入旧 RAG 业务链路。</p>
 */
@Deprecated
@RestController
@RequestMapping("/api/knowledge")
public class LegacyRagController {

    @PostMapping("/chat")
    public Mono<ResponseEntity<Map<String, Object>>> ragChat() {
        return endpointClosed();
    }

    @PostMapping("/chat/stream")
    public Mono<ResponseEntity<Map<String, Object>>> ragChatStream() {
        return endpointClosed();
    }

    private Mono<ResponseEntity<Map<String, Object>>> endpointClosed() {
        Map<String, Object> body = Map.of(
                "code", HttpStatus.GONE.value(),
                "errorCode", "LEGACY_RAG_ENDPOINT_RETIRED",
                "message", "该接口已停用，请改用 /api/ai/chat",
                "replacement", "/api/ai/chat"
        );
        return Mono.just(ResponseEntity.status(HttpStatus.GONE)
                .header("Deprecation", "true")
                .header(HttpHeaders.LINK, "</api/ai/chat>; rel=\"successor-version\"")
                .body(body));
    }
}
