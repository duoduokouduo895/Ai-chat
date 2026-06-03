package org.example.ai_agent.service.registry;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.ai.ModelCatalogProperties;
import org.example.ai_agent.config.ai.ModelCatalogProperties.Metadata;
import org.example.ai_agent.config.ai.ModelCatalogProperties.ModelConfig;
import org.example.ai_agent.config.ai.ModelCatalogProperties.ModelType;
import org.example.ai_agent.config.ai.http.Utf8SpringRestClientBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 模型运行时注册中心。
 * 先完整构建并校验新快照，再原子替换旧快照，避免刷新期间暴露半成品状态。
 */
@Slf4j
@Service
public class AiModelRegistry {

    private static final String CONFIG_PREFIX = "langchain4j.open-ai";

    private final ModelCatalogProperties properties;
    private final Executor aiStreamingExecutor;
    private final AtomicReference<ModelSnapshot> snapshot = new AtomicReference<>(ModelSnapshot.empty());

    public AiModelRegistry(ModelCatalogProperties properties,
                           @Qualifier("aiStreamingExecutor") Executor aiStreamingExecutor) {
        this.properties = properties;
        this.aiStreamingExecutor = aiStreamingExecutor;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @EventListener
    public void handleEnvironmentChange(EnvironmentChangeEvent event) {
        if (event.getKeys().stream().anyMatch(key -> key.startsWith(CONFIG_PREFIX))) {
            refresh();
        }
    }

    /**
     * 从当前配置重建模型目录。只有完整校验成功后才会替换旧快照。
     */
    public synchronized void refresh() {
        ModelSnapshot next = buildSnapshot();
        snapshot.set(next);
        log.info("AI模型目录已刷新: chatModels={}, embeddingModels={}, defaultChat={}, defaultEmbedding={}",
                next.chatModels().keySet(),
                next.embeddingModels().keySet(),
                next.defaultChatModel(),
                next.defaultEmbeddingModel());
    }

    public StreamingChatModel getModel(String modelAlias) {
        return getStreamingModel(modelAlias);
    }

    public StreamingChatModel getStreamingModel(String modelAlias) {
        ModelSnapshot current = snapshot.get();
        String resolvedAlias = resolveAlias(modelAlias, current.chatConfigs(), ModelType.CHAT);
        StreamingChatModel model = current.chatModels().get(resolvedAlias);
        if (model == null) {
            throw new IllegalArgumentException("找不到指定的聊天模型: " + modelAlias);
        }
        return model;
    }

    public EmbeddingModel getEmbeddingModel(String modelAlias) {
        ModelSnapshot current = snapshot.get();
        String resolvedAlias = resolveAlias(modelAlias, current.embeddingConfigs(), ModelType.EMBEDDING);
        EmbeddingModel model = current.embeddingModels().get(resolvedAlias);
        if (model == null) {
            throw new IllegalArgumentException("找不到指定的向量化模型: " + modelAlias);
        }
        return model;
    }

    public ModelConfig getEmbeddingModelConfig(String modelAlias) {
        ModelSnapshot current = snapshot.get();
        String resolvedAlias = resolveAlias(modelAlias, current.embeddingConfigs(), ModelType.EMBEDDING);
        ModelConfig config = current.embeddingConfigs().get(resolvedAlias);
        if (config == null) {
            throw new IllegalArgumentException("找不到指定的向量化模型配置: " + modelAlias);
        }
        return config;
    }

    public String resolveEmbeddingAlias(String modelAlias) {
        ModelSnapshot current = snapshot.get();
        String resolvedAlias = resolveAlias(modelAlias, current.embeddingConfigs(), ModelType.EMBEDDING);
        if (!current.embeddingConfigs().containsKey(resolvedAlias)) {
            throw new IllegalArgumentException("找不到指定的向量化模型配置: " + modelAlias);
        }
        return resolvedAlias;
    }

    public String getDefaultChatModel() {
        return snapshot.get().defaultChatModel();
    }

    public String getDefaultEmbeddingModel() {
        return snapshot.get().defaultEmbeddingModel();
    }

    public Set<String> getAvailableModels() {
        return snapshot.get().chatModels().keySet();
    }

    public Set<String> getAvailableEmbeddingModels() {
        return snapshot.get().embeddingModels().keySet();
    }

    public List<Metadata> getModelMetadata() {
        return snapshot.get().chatMetadata();
    }

    private ModelSnapshot buildSnapshot() {
        requireText(properties.getApiKey(), "langchain4j.open-ai.api-key");
        requireText(properties.getBaseUrl(), "langchain4j.open-ai.base-url");
        Map<String, ModelConfig> configuredModels = properties.getModels();
        if (configuredModels == null || configuredModels.isEmpty()) {
            throw new IllegalStateException("未配置任何 AI 模型");
        }
        configuredModels = prepareMigrationCompatibleModels(configuredModels);

        Map<String, StreamingChatModel> chatModels = new LinkedHashMap<>();
        Map<String, EmbeddingModel> embeddingModels = new LinkedHashMap<>();
        Map<String, ModelConfig> chatConfigs = new LinkedHashMap<>();
        Map<String, ModelConfig> embeddingConfigs = new LinkedHashMap<>();
        List<Metadata> chatMetadata = new ArrayList<>();

        configuredModels.forEach((alias, config) -> {
            if (config == null || !config.isEnabled()) {
                return;
            }

            validateCommonConfig(alias, config);
            if (config.getType() == ModelType.CHAT) {
                chatModels.put(alias, createChatModel(config));
                chatConfigs.put(alias, config);
                chatMetadata.add(buildMetadata(alias, config));
            } else if (config.getType() == ModelType.EMBEDDING) {
                validateEmbeddingConfig(alias, config);
                embeddingModels.put(alias, createEmbeddingModel(config));
                embeddingConfigs.put(alias, config);
            }
        });

        String defaultChatModel = resolveConfiguredDefault(
                properties.getDefaultChatModel(), chatModels, ModelType.CHAT);
        String defaultEmbeddingModel = resolveConfiguredDefault(
                properties.getDefaultEmbeddingModel(), embeddingModels, ModelType.EMBEDDING);

        return new ModelSnapshot(
                Map.copyOf(chatModels),
                Map.copyOf(embeddingModels),
                Map.copyOf(chatConfigs),
                Map.copyOf(embeddingConfigs),
                List.copyOf(chatMetadata),
                defaultChatModel,
                defaultEmbeddingModel
        );
    }

    private Map<String, ModelConfig> prepareMigrationCompatibleModels(Map<String, ModelConfig> source) {
        Map<String, ModelConfig> prepared = new LinkedHashMap<>(source);
        prepared.forEach((alias, config) -> {
            if (config == null) {
                return;
            }
            if (config.getType() == null) {
                config.setType(ModelType.CHAT);
                log.warn("模型 {} 使用旧配置格式，已临时按 CHAT 类型加载，请迁移 Nacos 配置", alias);
            }
            if (!StringUtils.hasText(config.getProviderModelName()) && StringUtils.hasText(config.getModelName())) {
                config.setProviderModelName(config.getModelName());
                log.warn("模型 {} 仍使用旧键 model-name，请迁移为 provider-model-name", alias);
            }
        });

        boolean hasEmbeddingModel = prepared.values().stream()
                .anyMatch(config -> config != null && config.isEnabled() && config.getType() == ModelType.EMBEDDING);
        if (!hasEmbeddingModel) {
            ModelCatalogProperties.Legacy legacy = properties.getLegacy();
            ModelConfig embedding = new ModelConfig();
            embedding.setType(ModelType.EMBEDDING);
            embedding.setEnabled(true);
            embedding.setProviderModelName(legacy.getEmbeddingProviderModelName());
            embedding.setDimension(legacy.getEmbeddingDimension());
            embedding.setVersion(legacy.getEmbeddingVersion());
            prepared.putIfAbsent(properties.getDefaultEmbeddingModel(), embedding);
            log.warn("未配置 EMBEDDING 模型，已启用迁移兼容模型 {}，请补齐 Nacos 模型目录",
                    properties.getDefaultEmbeddingModel());
        }
        return prepared;
    }

    private StreamingChatModel createChatModel(ModelConfig config) {
        if (config.getTemperature() == null) {
            throw new IllegalStateException("聊天模型缺少 temperature: " + config.getProviderModelName());
        }
        if (config.getMaxTokens() == null || config.getMaxTokens() <= 0) {
            throw new IllegalStateException("聊天模型 max-tokens 必须大于 0: " + config.getProviderModelName());
        }

        return OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new Utf8SpringRestClientBuilder(aiStreamingExecutor))
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(config.getProviderModelName())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .timeout(Duration.ofMillis(config.getTimeout()))
                .build();
    }

    private EmbeddingModel createEmbeddingModel(ModelConfig config) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(config.getProviderModelName())
                .timeout(Duration.ofMillis(config.getTimeout()))
                .build();
    }

    private Metadata buildMetadata(String alias, ModelConfig config) {
        Metadata source = config.getMetadata() != null ? config.getMetadata() : new Metadata();
        Metadata metadata = new Metadata();
        metadata.setLabel(StringUtils.hasText(source.getLabel()) ? source.getLabel() : alias);
        metadata.setDescription(source.getDescription());
        metadata.setSpeed(source.getSpeed());
        metadata.setValue(alias);
        metadata.setType(ModelType.CHAT);
        return metadata;
    }

    private void validateCommonConfig(String alias, ModelConfig config) {
        requireText(alias, "模型别名");
        if (config.getType() == null) {
            throw new IllegalStateException("模型缺少 type: " + alias);
        }
        requireText(config.getProviderModelName(), "模型 provider-model-name: " + alias);
        if (config.getTimeout() == null || config.getTimeout() <= 0) {
            throw new IllegalStateException("模型 timeout 必须大于 0: " + alias);
        }
    }

    private void validateEmbeddingConfig(String alias, ModelConfig config) {
        requireText(config.getVersion(), "向量化模型 version: " + alias);
        if (config.getDimension() == null || config.getDimension() <= 0) {
            throw new IllegalStateException("向量化模型 dimension 必须大于 0: " + alias);
        }
    }

    private String resolveConfiguredDefault(String alias, Map<String, ?> models, ModelType type) {
        if (!StringUtils.hasText(alias) && type == ModelType.CHAT && !models.isEmpty()) {
            String firstAlias = models.keySet().iterator().next();
            log.warn("未配置默认 CHAT 模型，迁移期间临时使用 {}", firstAlias);
            return firstAlias;
        }
        if (!models.containsKey(alias)) {
            throw new IllegalStateException("默认 " + type + " 模型未启用或不存在: " + alias);
        }
        return alias;
    }

    private String resolveAlias(String requestedAlias, Map<String, ModelConfig> configs, ModelType type) {
        if (!StringUtils.hasText(requestedAlias)) {
            return type == ModelType.CHAT ? getDefaultChatModel() : getDefaultEmbeddingModel();
        }

        String normalized = requestedAlias.trim();
        if (configs.containsKey(normalized)) {
            return normalized;
        }

        return configs.entrySet().stream()
                .filter(entry -> normalized.equals(entry.getValue().getProviderModelName()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(normalized);
    }

    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("缺少必要配置: " + name);
        }
    }

    private record ModelSnapshot(
            Map<String, StreamingChatModel> chatModels,
            Map<String, EmbeddingModel> embeddingModels,
            Map<String, ModelConfig> chatConfigs,
            Map<String, ModelConfig> embeddingConfigs,
            List<Metadata> chatMetadata,
            String defaultChatModel,
            String defaultEmbeddingModel
    ) {
        private static ModelSnapshot empty() {
            return new ModelSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), List.of(), null, null);
        }
    }
}
