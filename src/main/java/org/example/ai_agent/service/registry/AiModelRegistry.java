package org.example.ai_agent.service.registry;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.ai.AiModelProperties;
import org.example.ai_agent.config.ai.http.Utf8SpringRestClientBuilder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI模型注册中心服务
 * <p>
 * 负责根据配置动态创建、注册和管理所有可用的AI模型实例。
 */
@Slf4j
@Service
public class AiModelRegistry {

    private final AiModelProperties aiModelProperties;
    private final Map<String, StreamingChatModel> streamingModels = new ConcurrentHashMap<>();

    public AiModelRegistry(AiModelProperties aiModelProperties) {
        this.aiModelProperties = aiModelProperties;
    }

    /**
     * 在服务初始化时，根据配置创建并注册所有模型。
     */
    @PostConstruct
    public void init() {
        log.info("开始初始化AI模型注册中心...");
        
        // 详细记录配置信息
        log.info("AiModelProperties配置信息:");
        log.info("  - apiKey: {}", aiModelProperties.getApiKey() != null ? "已设置" : "未设置");
        log.info("  - baseUrl: {}", aiModelProperties.getBaseUrl());
        log.info("  - models数量: {}", aiModelProperties.getModels() != null ? aiModelProperties.getModels().size() : 0);
        
        // 检查models映射是否为空
        if (aiModelProperties.getModels() == null || aiModelProperties.getModels().isEmpty()) {
            log.warn("⚠️ 在AiModelProperties中没有找到任何模型配置。");
            log.warn("当前配置前缀: langchain4j.open-ai");
            log.warn("请确保Nacos中有对应的配置项");
            log.warn("⚠️ 模型注册中心将继续运行，但无可用模型。系统可能无法正常工作。");
            return; // 优雅地退出，不抛出异常
        }

        // 从配置中获取全局的apiKey和baseUrl
        String globalApiKey = aiModelProperties.getApiKey();
        String globalBaseUrl = aiModelProperties.getBaseUrl();
        
        // 验证关键配置
        if (globalApiKey == null || globalApiKey.trim().isEmpty()) {
            log.warn("⚠️ API Key未配置或为空！请检查Nacos配置中的langchain4j.open-ai.api-key");
            log.warn("系统将继续运行，但可能无法正常使用AI功能。");
            return;
        }
        
        if (globalBaseUrl == null || globalBaseUrl.trim().isEmpty()) {
            log.warn("⚠️ Base URL未配置或为空！请检查Nacos配置中的langchain4j.open-ai.base-url");
            log.warn("系统将继续运行，但可能无法正常使用AI功能。");
            return;
        }
        
        log.info("全局配置验证通过:");
        log.info("  - API Key: {}...{}", globalApiKey.substring(0, 8), globalApiKey.substring(globalApiKey.length() - 4));
        log.info("  - Base URL: {}", globalBaseUrl);

        // 遍历配置中的每一个模型条目
        aiModelProperties.getModels().forEach((modelAlias, config) -> {
            try {
                log.info("正在创建模型: {} (别名: {})", config.getModelName(), modelAlias);
                log.info("  - 模型名称: {}", config.getModelName());
                log.info("  - 温度: {}", config.getTemperature());
                log.info("  - 最大Token: {}", config.getMaxTokens());
                log.info("  - 超时时间: {}ms", config.getTimeout());
                
                // 使用全局的baseUrl和apiKey，以及每个模型自己的参数来构建模型
                StreamingChatModel model = OpenAiStreamingChatModel.builder()
                        .httpClientBuilder(new Utf8SpringRestClientBuilder())
                        .baseUrl(globalBaseUrl)
                        .apiKey(globalApiKey)
                        .modelName(config.getModelName())
                        .temperature(config.getTemperature())
                        .maxTokens(config.getMaxTokens())
                        .timeout(Duration.ofMillis(config.getTimeout()))
                        .build();

                // 使用模型全名作为Key，方便前端直接调用
                streamingModels.put(config.getModelName(), model);
                log.info("成功注册模型: '{}' (别名: {})", config.getModelName(), modelAlias);

            } catch (Exception e) {
                log.error("创建模型 '{}' (别名: {}) 失败: {}", config.getModelName(), modelAlias, e.getMessage(), e);
            }
        });
        log.info("AI模型注册中心初始化完成，共注册 {} 个模型。", streamingModels.size());
        
        // 输出所有已注册的模型
        if (!streamingModels.isEmpty()) {
            log.info("已注册的模型列表:");
            streamingModels.keySet().forEach(modelName -> log.info("  - {}", modelName));
        }
    }

    /**
     * 根据模型ID获取一个流式聊天模型实例。
     *
     * @param modelId 模型的完整名称, e.g., "zai-org/GLM-4.5-Air"
     * @return StreamingChatModel实例
     * @throws IllegalArgumentException 如果模型ID不存在
     */
    public StreamingChatModel getModel(String modelId) {
        StreamingChatModel model = streamingModels.get(modelId);
        if (model == null) {
            log.warn("请求一个未注册或不存在的模型ID: {}", modelId);
            throw new IllegalArgumentException("找不到指定的AI模型: " + modelId);
        }
        return model;
    }
    
    /**
     * 根据模型ID获取一个流式聊天模型实例（别名方法，与getModel功能相同）。
     *
     * @param modelId 模型的完整名称, e.g., "zai-org/GLM-4.5-Air"
     * @return StreamingChatModel实例
     * @throws IllegalArgumentException 如果模型ID不存在
     */
    public StreamingChatModel getStreamingModel(String modelId) {
        return getModel(modelId);
    }

    /**
     * 获取所有可用的模型ID集合。
     *
     * @return 模型ID集合
     */
    public Set<String> getAvailableModels() {
        return streamingModels.keySet();
    }

    /**
     * 获取所有可用模型的前端元数据列表。
     *
     * @return 模型元数据列表
     */
    public java.util.List<AiModelProperties.Metadata> getModelMetadata() {
        if (aiModelProperties.getModels() == null) {
            return java.util.Collections.emptyList();
        }
        return aiModelProperties.getModels().values().stream()
                .map(AiModelProperties.ModelConfig::getMetadata)
                .collect(java.util.stream.Collectors.toList());
    }
}
