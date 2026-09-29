package org.example.ai_agent.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 模型目录配置。
 * 业务侧始终使用稳定别名，供应商模型名称仅作为可替换的配置细节。
 */
@Data
@Component
@ConfigurationProperties(prefix = "langchain4j.open-ai")
public class ModelCatalogProperties {

    private String apiKey;
    private String baseUrl;
    private String defaultChatModel;
    private String defaultEmbeddingModel = "embedding-default";
    private Streaming streaming = new Streaming();
    private Reliability reliability = new Reliability();
    private Legacy legacy = new Legacy();
    private Map<String, ModelConfig> models = new LinkedHashMap<>();

    public enum ModelType {
        CHAT,
        EMBEDDING
    }

    @Data
    public static class Streaming {
        private int corePoolSize = 8;
        private int maxPoolSize = 32;
        private int queueCapacity = 200;
        private int keepAliveSeconds = 60;
    }

    @Data
    public static class Reliability {
        private boolean retryEnabled = true;
        private int retryMaxAttempts = 2;
        private long retryBackoffMs = 500;
        private boolean warmupEnabled = true;
        private String warmupHost;
    }

    /**
     * 旧模型目录配置格式的短期兼容参数。
     * 完成线上配置迁移后可以删除。
     */
    @Data
    public static class Legacy {
        private String embeddingProviderModelName = "Qwen/Qwen3-Embedding-8B";
        private int embeddingDimension = 4096;
        private String embeddingVersion = "legacy-qwen3-embedding-8b";
    }

    @Data
    public static class ModelConfig {
        private Integer inputBudgetTokens = 8192;
        /** Reserved for tool request/response messages that are appended by the AI service. */
        private Integer toolOutputReserveTokens = 1024;
        /** Explicit compatible tokenizer model; absent uses a conservative UTF-8 byte estimate. */
        private String tokenEstimatorModel;
        private ModelType type;
        private boolean enabled = true;
        private String providerModelName;
        /**
         * 兼容旧配置键 models.*.model-name，迁移完成后删除。
         */
        @Deprecated
        private String modelName;
        private Double temperature;
        private Integer maxTokens;
        private Integer timeout = 15_000;
        private Integer dimension;
        private String version;
        private Metadata metadata = new Metadata();
    }

    @Data
    public static class Metadata {
        private String label;
        private String description;
        private String speed;
        private String value;
        private ModelType type;
    }
}
