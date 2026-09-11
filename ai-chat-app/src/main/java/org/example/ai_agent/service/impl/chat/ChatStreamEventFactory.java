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
                "protocolVersion", 2,
                "conversationId", request.getConversationId(),
                "mode", request.isRagMode() ? "rag" : "normal",
                "model", request.getEffectiveModelId()
        );
        return ServerSentEvent.<String>builder()
                .event("metadata")
                .data(toJson(metadata))
                .build();
    }

    public ServerSentEvent<String> retrieval(ChatRetrievalReader.View view) {
        return ServerSentEvent.<String>builder().event("retrieval")
                .data(toJson(Map.of("status", view.status(), "channels", view.channels(), "sources", view.sources()))).build();
    }

    public ServerSentEvent<String> complete(String turnId) {
        // Old frontends treat unknown event names as message text. [DONE] is already
        // a stop marker there, so it must not be a JSON payload.
        return ServerSentEvent.<String>builder()
                .id(turnId)
                .event("complete")
                .data("[DONE]")
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
        String errorCode = "CHAT_ERROR";
        String message = "聊天处理失败，请稍后重试或切换模型";
        if (ex instanceof ChatStreamClientException clientException) {
            errorCode = clientException.getErrorCode();
            message = clientException.getMessage();
        }
        Map<String, Object> errorPayload = Map.of(
                "type", "error",
                "code", errorCode,
                "message", message
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
