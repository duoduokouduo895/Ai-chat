package org.example.ai_agent.service.assistant;

import dev.langchain4j.service.AiServices;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.memory.ConversationMemoryBlock;
import org.example.ai_agent.service.memory.ConversationMemoryService;
import org.example.ai_agent.service.provider.ChatMemoryProvider;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.springframework.stereotype.Service;

/**
 * 创建带有会话级上下文的 AI Service 代理。
 */
@Service
public class GeneralAssistantFactory {

    private final AiModelRegistry aiModelRegistry;
    private final ChatMemoryProvider chatMemoryProvider;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentService documentService;
    private final ConversationMemoryService conversationMemory;

    public GeneralAssistantFactory(AiModelRegistry aiModelRegistry,
                                   ChatMemoryProvider chatMemoryProvider,
                                   KnowledgeBaseService knowledgeBaseService,
                                   DocumentService documentService,
                                   ConversationMemoryService conversationMemory) {
        this.aiModelRegistry = aiModelRegistry;
        this.chatMemoryProvider = chatMemoryProvider;
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentService = documentService;
        this.conversationMemory = conversationMemory;
    }

    public GeneralAssistant createAssistant(ChatRequest chatRequest) {
        String effectiveModelId = normalizeModelId(chatRequest.getEffectiveModelId());
        if (chatRequest.isRagMode()) {
            // RAG 上下文已经由 AssistantInputBuilder 完成检索并写入提示词。
            // 再暴露知识库工具不仅重复检索，还会让部分 OpenAI-compatible
            // 流式模型把工具协议标记当作普通文本输出。
            return createAssistant(effectiveModelId, null, chatRequest);
        }

        KnowledgeAssistantToolGateway toolGateway = new KnowledgeAssistantToolGateway(
                chatRequest.getUserId(),
                chatRequest.isAdminRequest(),
                chatRequest.getKnowledgeBaseIds(),
                knowledgeBaseService,
                documentService
        );
        KnowledgeAssistantTools knowledgeTools = new KnowledgeAssistantTools(toolGateway);
        return createAssistant(effectiveModelId, knowledgeTools, chatRequest);
    }

    private GeneralAssistant createAssistant(String modelId, KnowledgeAssistantTools knowledgeTools, ChatRequest request) {
        // Keep the system prompt ahead of history; by default it is appended after pre-loaded turns.
        var memory = dev.langchain4j.memory.chat.MessageWindowChatMemory.builder()
                .maxMessages(Math.max(256, (request.getContextTurns() == null ? 0 : request.getContextTurns().size() * 2) + 64))
                .alwaysKeepSystemMessageFirst(true)
                .build();
        if (request.getMemoryBlock() != null) {
            // Older turns replaced by a summary: background data precedes the recent originals.
            memory.add(dev.langchain4j.data.message.UserMessage.from(request.getMemoryBlock()));
            memory.add(dev.langchain4j.data.message.AiMessage.from(ConversationMemoryBlock.ACKNOWLEDGEMENT));
        }
        if (request.getContextTurns() != null) {
            for (var turn : request.getContextTurns()) {
                memory.add(dev.langchain4j.data.message.UserMessage.from(turn.userText()));
                memory.add(dev.langchain4j.data.message.AiMessage.from(turn.assistantText()));
            }
        }
        dev.langchain4j.memory.chat.ChatMemoryProvider memoryProvider =
                memoryId -> memory;

        var builder = AiServices.builder(GeneralAssistant.class)
                .streamingChatModel(new InputBudgetStreamingChatModel(aiModelRegistry.getStreamingModel(modelId),
                        request.getResolvedInputBudgetTokens(), request.getResolvedTokenEstimatorModel()))
                .chatMemoryProvider(memoryProvider);
        if (knowledgeTools != null && request.isHistoryRecallAvailable()) {
            builder.tools(knowledgeTools, new ConversationHistoryTools(request.getConversationId(), conversationMemory));
        } else if (knowledgeTools != null) {
            builder.tools(knowledgeTools);
        }
        return builder.build();
    }

    private String normalizeModelId(String modelId) {
        String effectiveModelId = modelId != null ? modelId.trim() : "";
        if (effectiveModelId.isEmpty()) {
            throw new IllegalArgumentException("模型ID不能为空");
        }
        return effectiveModelId;
    }
}
