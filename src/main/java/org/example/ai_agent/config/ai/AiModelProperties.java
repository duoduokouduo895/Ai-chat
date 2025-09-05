package org.example.ai_agent.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 将Nacos中的AI模型配置动态绑定到Java对象。
 * 通过@ConfigurationProperties注解，将以"langchain4j.open-ai"为前缀的配置
 * 自动映射到一个Map中，其中key是模型的别名（如"glm-air"），
 * value是包含该模型所有参数的ModelConfig对象。
 */
@Component
@RefreshScope
@ConfigurationProperties(prefix = "langchain4j.open-ai")
@Data
public class AiModelProperties {

    /**
     * 全局API密钥。
     */
    private String apiKey;

    /**
     * 全局API基础URL。
     */
    private String baseUrl;

    /**
     * 存储所有模型配置的Map。
     * Key: 模型的唯一别名 (e.g., "glm-air", "deepseek-v3")
     * Value: 模型的具体配置对象
     */
    private Map<String, ModelConfig> models;

    /**
     * 单个模型的完整配置。
     */
    @Data
    public static class ModelConfig {
        /**
         * 模型在服务提供商处的完整名称。
         * e.g., "zai-org/GLM-4.5-Air"
         */
        private String modelName;

        /**
         * 模型推理的温度参数，控制创造性。
         */
        private Double temperature;

        /**
         * 模型生成的最大Token数。
         */
        private Integer maxTokens;

        /**
         * API请求的超时时间（毫秒）。
         */
        private Integer timeout;

        /**
         * 用于前端展示的元数据。
         */
        private Metadata metadata;
    }

    /**
     * 模型的前端展示元数据。
     * 这些信息将通过/models接口暴露给前端UI。
     */
    @Data
    public static class Metadata {
        /**
         * 在UI下拉列表中显示的名称。
         * e.g., "GLM-4.5-Air"
         */
        private String label;

        /**
         * 模型的简短描述。
         * e.g., "超快响应模型，适合快速对话（推荐）"
         */
        private String description;

        /**
         * 模型的速度标识，用于UI显示。
         * e.g., "fast", "medium", "slow"
         */
        private String speed;
        
        /**
         * 传递给后端API的模型唯一标识。
         * e.g., "zai-org/GLM-4.5-Air"
         */
        private String value;
    }
}