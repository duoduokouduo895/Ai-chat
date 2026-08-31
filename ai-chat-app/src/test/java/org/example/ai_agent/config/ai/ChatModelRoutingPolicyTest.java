package org.example.ai_agent.config.ai;

import org.example.ai_agent.dto.chat.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatModelRoutingPolicyTest {

    @Test
    void ragUsesTheConfiguredContractTestedModel() {
        ChatModelRoutingPolicy policy = new ChatModelRoutingPolicy("chat-reasoning");
        ChatRequest request = ChatRequest.builder()
                .model("chat-fast")
                .knowledgeBaseIds(List.of(7L))
                .build();

        assertEquals("chat-reasoning", policy.resolve(request));
    }

    @Test
    void normalChatKeepsTheRequestedModel() {
        ChatModelRoutingPolicy policy = new ChatModelRoutingPolicy("chat-reasoning");
        ChatRequest request = ChatRequest.builder().model("chat-fast").build();

        assertEquals("chat-fast", policy.resolve(request));
    }

    @Test
    void blankRagModelFailsAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new ChatModelRoutingPolicy("  "));
    }
}
