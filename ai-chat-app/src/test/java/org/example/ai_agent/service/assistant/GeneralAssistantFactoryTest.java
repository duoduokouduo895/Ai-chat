package org.example.ai_agent.service.assistant;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.provider.ChatMemoryProvider;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneralAssistantFactoryTest {

    private final AtomicReference<ChatRequest> capturedRequest = new AtomicReference<>();
    private GeneralAssistantFactory factory;

    @BeforeEach
    void setUp() {
        AiModelRegistry modelRegistry = mock(AiModelRegistry.class);
        ChatMemoryProvider memoryProvider = mock(ChatMemoryProvider.class);
        when(modelRegistry.getStreamingModel("chat-fast")).thenReturn(capturingModel());
        when(memoryProvider.get(anyString())).thenAnswer(invocation -> MessageWindowChatMemory.withMaxMessages(20));

        factory = new GeneralAssistantFactory(
                modelRegistry,
                memoryProvider,
                mock(KnowledgeBaseService.class),
                mock(DocumentService.class)
        );
    }

    @Test
    void ragAssistantDoesNotExposeToolsAfterContextWasAlreadyRetrieved() {
        org.example.ai_agent.dto.chat.ChatRequest request = baseRequest();
        request.setKnowledgeBaseIds(java.util.List.of(7L));

        factory.createAssistant(request).chat("conversation", "参考资料：唯一指纹").collectList().block();

        assertTrue(capturedRequest.get().toolSpecifications().isEmpty());
    }

    @Test
    void normalAssistantStillExposesKnowledgeTools() {
        factory.createAssistant(baseRequest()).chat("conversation", "列出我的知识库").collectList().block();

        assertFalse(capturedRequest.get().toolSpecifications().isEmpty());
    }

    private org.example.ai_agent.dto.chat.ChatRequest baseRequest() {
        return org.example.ai_agent.dto.chat.ChatRequest.builder()
                .userId(42L)
                .model("chat-fast")
                .prompt("测试")
                .build();
    }

    private StreamingChatModel capturingModel() {
        return new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                capturedRequest.set(request);
                handler.onPartialResponse("完成");
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from("完成"))
                        .build());
            }
        };
    }
}
