package org.example.ai_agent.service.assistant;

import dev.langchain4j.service.AiServices;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.KnowledgeBaseService;
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

    public GeneralAssistantFactory(AiModelRegistry aiModelRegistry,
                                   ChatMemoryProvider chatMemoryProvider,
                                   KnowledgeBaseService knowledgeBaseService,
                                   DocumentService documentService) {
        this.aiModelRegistry = aiModelRegistry;
        this.chatMemoryProvider = chatMemoryProvider;
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentService = documentService;
    }

    public GeneralAssistant createAssistant(ChatRequest chatRequest) {
        String effectiveModelId = normalizeModelId(chatRequest.getEffectiveModelId());
        if (chatRequest.isRagMode()) {
            // RAG 上下文已经由 AssistantInputBuilder 完成检索并写入提示词。
            // 再暴露知识库工具不仅重复检索，还会让部分 OpenAI-compatible
            // 流式模型把工具协议标记当作普通文本输出。
            return createAssistant(effectiveModelId, null);
        }

        KnowledgeAssistantToolGateway toolGateway = new KnowledgeAssistantToolGateway(
                chatRequest.getUserId(),
                chatRequest.isAdminRequest(),
                chatRequest.getKnowledgeBaseIds(),
                knowledgeBaseService,
                documentService
        );
        KnowledgeAssistantTools knowledgeTools = new KnowledgeAssistantTools(toolGateway);
        return createAssistant(effectiveModelId, knowledgeTools);
    }

    private GeneralAssistant createAssistant(String modelId, KnowledgeAssistantTools knowledgeTools) {
        dev.langchain4j.memory.chat.ChatMemoryProvider memoryProvider =
                memoryId -> chatMemoryProvider.get(String.valueOf(memoryId));

        var builder = AiServices.builder(GeneralAssistant.class)
                .streamingChatModel(aiModelRegistry.getStreamingModel(modelId))
                .chatMemoryProvider(memoryProvider);
        if (knowledgeTools != null) {
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
