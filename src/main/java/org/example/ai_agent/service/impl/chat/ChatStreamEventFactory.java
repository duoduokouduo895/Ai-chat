package org.example.ai_agent.service.impl.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChatStreamEventFactory {

    private final ObjectMapper objectMapper;

    public ChatStreamEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ServerSentEvent<String> metadata(ChatRequest request) {
        Map<String, Object> metadata = Map.of(
                "type", "metadata",
                "conversationId", request.getConversationId(),
                "mode", request.isRagMode() ? "rag" : "normal"
        );
        return ServerSentEvent.<String>builder()
                .event("metadata")
                .data(toJson(metadata))
                .build();
    }

    public ServerSentEvent<String> status(String message) {
        return ServerSentEvent.<String>builder()
                .event("status")
                .data(toJson(Map.of("type", "status", "message", message)))
                .build();
    }

    public ServerSentEvent<String> message(String chunk) {
        return ServerSentEvent.<String>builder()
                .event("message")
                .data(chunk)
                .build();
    }

    public ServerSentEvent<String> error(Throwable ex) {
        Map<String, Object> errorPayload = Map.of(
                "type", "error",
                "code", "CHAT_ERROR",
                "message", "聊天处理失败：" + ex.getMessage()
        );
        return ServerSentEvent.<String>builder()
                .event("error")
                .data(toJson(errorPayload))
                .build();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"code\":\"JSON_SERIALIZATION_ERROR\",\"message\":\"响应序列化失败\"}";
        }
    }
}
