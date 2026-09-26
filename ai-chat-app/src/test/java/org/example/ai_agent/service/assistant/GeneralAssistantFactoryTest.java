package org.example.ai_agent.service.assistant;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.provider.ChatMemoryProvider;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.example.ai_agent.config.ai.ModelCatalogProperties;
import org.example.ai_agent.conversation.CompletedContextTurn;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.example.ai_agent.service.impl.chat.ConversationContextLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                mock(DocumentService.class),
                org.example.ai_agent.service.memory.MemoryTestSupport.disabled()
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

    @Test
    void preparedRagHistoryAtExactBudgetReachesProvider() {
        var request = baseRequest();
        request.setConversationId(8L);
        request.setKnowledgeBaseIds(List.of(7L));
        var recent = new CompletedContextTurn("recent", "关于 Java 21", "使用 MySQL", LocalDateTime.now());
        var budget = new AssistantInputBudget(null, null);
        int limit = (int) budget.estimateInput(List.of(SystemMessage.from(GeneralAssistant.SYSTEM_PROMPT),
                UserMessage.from(recent.userText()), AiMessage.from(recent.assistantText()),
                UserMessage.from(request.getPrompt())), List.of());
        var repository = mock(MessageRepository.class);
        when(repository.findCompletedContext(8L)).thenReturn(Flux.just(recent));
        when(repository.findLegacyContext(8L)).thenReturn(Flux.empty());
        var properties = new ModelCatalogProperties();
        var config = new ModelCatalogProperties.ModelConfig();
        config.setInputBudgetTokens(limit);
        properties.getModels().put("chat-fast", config);

        var input = new ConversationContextLoader(repository, properties, org.example.ai_agent.service.memory.MemoryTestSupport.disabled()).prepare(request, request.getPrompt()).block();
        factory.createAssistant(request).chat("8:chat-fast", input).collectList().block();

        assertEquals(List.of(recent), request.getContextTurns());
        assertEquals(limit, request.getEstimatedInputTokens());
        assertEquals(limit, budget.estimateInput(capturedRequest.get().messages(), List.of()));
        assertEquals(1, capturedRequest.get().messages().stream()
                .filter(message -> message.equals(UserMessage.from(request.getPrompt()))).count());
    }

    @Test
    void historyFittingOldEstimateIsTrimmedBeforeProviderCall() {
        var request = baseRequest();
        request.setConversationId(8L);
        request.setKnowledgeBaseIds(List.of(7L));
        int limit = 2100;
        int userSize = limit - GeneralAssistant.SYSTEM_PROMPT.getBytes(StandardCharsets.UTF_8).length
                - request.getPrompt().getBytes(StandardCharsets.UTF_8).length - 32 - 16 - 1;
        var historical = new CompletedContextTurn("old", "u".repeat(userSize), "a", LocalDateTime.now());
        var repository = mock(MessageRepository.class);
        when(repository.findCompletedContext(8L)).thenReturn(Flux.just(historical));
        when(repository.findLegacyContext(8L)).thenReturn(Flux.empty());
        var properties = new ModelCatalogProperties();
        var config = new ModelCatalogProperties.ModelConfig();
        config.setInputBudgetTokens(limit);
        properties.getModels().put("chat-fast", config);

        var input = new ConversationContextLoader(repository, properties, org.example.ai_agent.service.memory.MemoryTestSupport.disabled()).prepare(request, request.getPrompt()).block();
        factory.createAssistant(request).chat("8:chat-fast", input).collectList().block();

        assertTrue(request.getContextTurns().isEmpty());
        assertTrue(new AssistantInputBudget(limit, null).estimateInput(capturedRequest.get().messages(), List.of()) <= limit);
    }

    @Test
    void tokenizedOrdinaryChatRetainsToolsAndOutputReserveWithinBudget() {
        var request = baseRequest();
        request.setConversationId(8L);
        var recent = new CompletedContextTurn("recent", "已有的问题", "已有的回答", LocalDateTime.now());
        var older = new CompletedContextTurn("older", "字".repeat(6000), "过长历史", recent.completedAt().minusSeconds(1));
        var repository = mock(MessageRepository.class);
        when(repository.findCompletedContext(8L)).thenReturn(Flux.just(recent, older));
        when(repository.findLegacyContext(8L)).thenReturn(Flux.empty());
        var properties = new ModelCatalogProperties();
        var config = new ModelCatalogProperties.ModelConfig();
        config.setInputBudgetTokens(2100);
        config.setToolOutputReserveTokens(128);
        config.setTokenEstimatorModel("gpt-4o");
        properties.getModels().put("chat-fast", config);

        var input = new ConversationContextLoader(repository, properties, org.example.ai_agent.service.memory.MemoryTestSupport.disabled()).prepare(request, request.getPrompt()).block();
        factory.createAssistant(request).chat("8:chat-fast", input).collectList().block();

        assertEquals(List.of(recent), request.getContextTurns());
        assertFalse(capturedRequest.get().toolSpecifications().isEmpty());
        var descriptions = capturedRequest.get().toolSpecifications().stream()
                .map(dev.langchain4j.agent.tool.ToolSpecification::toJson).toList();
        long sent = new AssistantInputBudget(2100, "gpt-4o").estimateInput(capturedRequest.get().messages(), descriptions);
        assertEquals(sent + 128, request.getEstimatedInputTokens().longValue());
        assertTrue(sent + 128 <= 2100);
    }

    @Test
    void memoryBlockPrecedesRecentTurnsAndRecallToolIsBudgeted() {
        var request = baseRequest();
        request.setConversationId(8L);
        var recent = new CompletedContextTurn("recent", "最近的问题", "最近的回答", LocalDateTime.now(), 30L);
        var repository = mock(MessageRepository.class);
        when(repository.findCompletedContext(8L)).thenReturn(Flux.just(recent));
        when(repository.findLegacyContext(8L)).thenReturn(Flux.empty());
        var store = mock(org.example.ai_agent.service.memory.ConversationMemoryStore.class);
        when(store.load(8L)).thenReturn(reactor.core.publisher.Mono.just(new org.example.ai_agent.service.memory.ConversationMemory(
                2, 20, "早期：用户决定使用 PostgreSQL。", List.of())));
        var memory = new org.example.ai_agent.service.memory.ConversationMemoryService(store,
                new org.example.ai_agent.service.memory.ConversationMemoryProperties());
        var properties = new ModelCatalogProperties();
        var config = new ModelCatalogProperties.ModelConfig();
        config.setInputBudgetTokens(4096);
        config.setToolOutputReserveTokens(128);
        config.setTokenEstimatorModel("gpt-4o");
        properties.getModels().put("chat-fast", config);

        var input = new ConversationContextLoader(repository, properties, memory).prepare(request, request.getPrompt()).block();
        factory.createAssistant(request).chat("8:chat-fast", input).collectList().block();

        var messages = capturedRequest.get().messages();
        assertTrue(messages.get(0) instanceof SystemMessage);
        assertEquals(UserMessage.from(request.getMemoryBlock()), messages.get(1));
        assertEquals(UserMessage.from(recent.userText()), messages.get(3));
        var descriptions = capturedRequest.get().toolSpecifications().stream()
                .map(dev.langchain4j.agent.tool.ToolSpecification::toJson).toList();
        assertTrue(capturedRequest.get().toolSpecifications().stream()
                .anyMatch(tool -> tool.name().equals("searchConversationHistory")));
        long sent = new AssistantInputBudget(4096, "gpt-4o").estimateInput(messages, descriptions);
        assertEquals(sent + 128, request.getEstimatedInputTokens().longValue());
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
