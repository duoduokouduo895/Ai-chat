package org.example.ai_agent.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * AI模型动态配置类
 * 支持通过Nacos动态调整AI模型参数
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "ai.model")
public class AiModelConfig {
    
    /**
     * GLM-4.5-Air模型配置
     */
    private GlmAirConfig glmAir = new GlmAirConfig();
    
    /**
     * DeepSeek-V3模型配置
     */
    private DeepSeekV3Config deepSeekV3 = new DeepSeekV3Config();
    
    /**
     * QwenLong-L1-32B模型配置
     */
    private QwenLongConfig qwenLong = new QwenLongConfig();
    
    /**
     * 默认模型配置
     */
    private DefaultConfig defaultConfig = new DefaultConfig();
    
    @Data
    public static class GlmAirConfig {
        private Double temperature = 0.3;  // 快速响应
        private Integer maxTokens = 6000;
    }
    
    @Data
    public static class DeepSeekV3Config {
        private Double temperature = 0.5;  // 平衡性能
        private Integer maxTokens = 8000;
    }
    
    @Data
    public static class QwenLongConfig {
        private Double temperature = 0.7;  // 深度思考
        private Integer maxTokens = 32000;
    }
    
    @Data
    public static class DefaultConfig {
        private Double temperature = 0.5;
        private Integer maxTokens = 4000;
    }
    
    /**
     * 根据模型类型获取对应的配置
     * 严格按照ai-model-config.properties中定义的支持模型列表进行匹配
     * 支持的模型：zai-org/GLM-4.5-Air, deepseek-ai/DeepSeek-V3, Tongyi-Zhiwen/QwenLong-L1-32B
     * 
     * @param modelType 模型类型，必须是完整的模型名称
     * @return 模型配置
     */
    public ModelConfigDto getModelConfig(String modelType) {
        if (modelType == null || modelType.trim().isEmpty()) {
            return ModelConfigDto.builder()
                    .temperature(defaultConfig.getTemperature())
                    .maxTokens(defaultConfig.getMaxTokens())
                    .build();
        }
        
        // 严格匹配完整的模型名称，不支持简化别名
        switch (modelType) {
            // GLM-4.5-Air模型 - 完整名称：zai-org/GLM-4.5-Air
            case "zai-org/GLM-4.5-Air":
                return ModelConfigDto.builder()
                        .temperature(glmAir.getTemperature())
                        .maxTokens(glmAir.getMaxTokens())
                        .build();
                        
            // DeepSeek-V3模型 - 完整名称：deepseek-ai/DeepSeek-V3
            case "deepseek-ai/DeepSeek-V3":
                return ModelConfigDto.builder()
                        .temperature(deepSeekV3.getTemperature())
                        .maxTokens(deepSeekV3.getMaxTokens())
                        .build();
                        
            // QwenLong-L1-32B模型 - 完整名称：Tongyi-Zhiwen/QwenLong-L1-32B
            case "Tongyi-Zhiwen/QwenLong-L1-32B":
                return ModelConfigDto.builder()
                        .temperature(qwenLong.getTemperature())
                        .maxTokens(qwenLong.getMaxTokens())
                        .build();
                        
            // 不支持的模型类型，返回默认配置
            default:
                return ModelConfigDto.builder()
                        .temperature(defaultConfig.getTemperature())
                        .maxTokens(defaultConfig.getMaxTokens())
                        .build();
        }
    }
    
    /**
     * 模型配置DTO
     */
    @Data
    @lombok.Builder
    public static class ModelConfigDto {
        private Double temperature;
        private Integer maxTokens;
    }
}