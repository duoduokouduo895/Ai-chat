package org.example.ai_agent.service.impl.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

class ChatStreamEventFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatStreamEventFactory eventFactory = new ChatStreamEventFactory(objectMapper);

    @Test
    void metadata_shouldKeepFrontendContract() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .conversationId(99L)
                .knowledgeBaseIds(List.of(1L))
                .build();

        var event = eventFactory.metadata(request);
        JsonNode json = objectMapper.readTree(event.data());

        org.junit.jupiter.api.Assertions.assertEquals("metadata", event.event());
        org.junit.jupiter.api.Assertions.assertEquals("metadata", json.get("type").asText());
        org.junit.jupiter.api.Assertions.assertEquals(99L, json.get("conversationId").asLong());
        org.junit.jupiter.api.Assertions.assertEquals("rag", json.get("mode").asText());
    }

    @Test
    void status_shouldKeepMessagePayload() throws Exception {
        var event = eventFactory.status("正在生成回答");
        JsonNode json = objectMapper.readTree(event.data());

        org.junit.jupiter.api.Assertions.assertEquals("status", event.event());
        org.junit.jupiter.api.Assertions.assertEquals("status", json.get("type").asText());
        org.junit.jupiter.api.Assertions.assertEquals("正在生成回答", json.get("message").asText());
    }

    @Test
    void message_shouldStreamRawChunk() {
        var event = eventFactory.message("保留  Markdown  空格");

        org.junit.jupiter.api.Assertions.assertEquals("message", event.event());
        org.junit.jupiter.api.Assertions.assertEquals("保留  Markdown  空格", event.data());
    }

    @Test
    void error_shouldKeepFrontendContractWithoutLeakingTheRootCause() throws Exception {
        var event = eventFactory.error(new RuntimeException("provider secret and internal address"));
        JsonNode json = objectMapper.readTree(event.data());

        org.junit.jupiter.api.Assertions.assertEquals("error", event.event());
        org.junit.jupiter.api.Assertions.assertEquals("error", json.get("type").asText());
        org.junit.jupiter.api.Assertions.assertEquals("CHAT_ERROR", json.get("code").asText());
        org.junit.jupiter.api.Assertions.assertEquals("聊天处理失败，请稍后重试或切换模型", json.get("message").asText());
        org.junit.jupiter.api.Assertions.assertFalse(json.get("message").asText().contains("provider secret"));
    }

    @Test
    void error_shouldExposeOnlyClassifiedClientErrors() throws Exception {
        var event = eventFactory.error(ChatStreamClientException.modelTimeout());
        JsonNode json = objectMapper.readTree(event.data());

        org.junit.jupiter.api.Assertions.assertEquals("CHAT_TIMEOUT", json.get("code").asText());
        org.junit.jupiter.api.Assertions.assertEquals("模型响应超时，请稍后重试或切换模型。", json.get("message").asText());
    }
}
