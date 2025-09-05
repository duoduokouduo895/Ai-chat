package org.example.ai_agent.dto.ai;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * AI模型信息DTO
 * <p>
 * 用于封装AI模型的详细信息，包括模型配置、状态和统计数据。
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelInfo {
    
    /**
     * 模型唯一标识符
     */
    private String modelId;
    
    /**
     * 模型名称
     */
    private String modelName;
    
    /**
     * 模型显示名称
     */
    private String displayName;
    
    /**
     * 模型描述
     */
    private String description;
    
    /**
     * 模型提供商
     */
    private String provider;
    
    /**
     * 模型版本
     */
    private String version;
    
    /**
     * 模型类型（如：chat, embedding, completion等）
     */
    private String type;
    
    /**
     * 是否为默认模型
     */
    @Builder.Default
    private Boolean isDefault = false;
    
    /**
     * 模型是否可用
     */
    @Builder.Default
    private Boolean available = true;
    
    /**
     * 模型状态（如：active, inactive, maintenance等）
     */
    @Builder.Default
    private String status = "active";
    
    /**
     * 最大输入token数
     */
    private Integer maxInputTokens;
    
    /**
     * 最大输出token数
     */
    private Integer maxOutputTokens;
    
    /**
     * 默认温度参数
     */
    @Builder.Default
    private Double temperature = 0.7;
    
    /**
     * 支持的功能列表（如：streaming, function_calling等）
     */
    private java.util.List<String> capabilities;
    
    /**
     * 模型配置信息
     */
    private ModelConfiguration configuration;
    
    /**
     * 模型使用统计
     */
    private ModelUsageStats usageStats;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 模型配置信息内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelConfiguration {
        private String baseUrl;
        private String apiKey;
        private Integer timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
    }
    
    /**
     * 模型使用统计内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelUsageStats {
        private Long totalRequests;
        private Long successfulRequests;
        private Long failedRequests;
        private Double averageResponseTime;
        private Long totalTokensUsed;
        private LocalDateTime lastUsed;
    }
    
    /**
     * 检查模型是否健康
     * 
     * @return 如果模型可用且状态为active返回true
     */
    public boolean isHealthy() {
        return available != null && available && "active".equals(status);
    }
    
    /**
     * 检查模型是否支持指定功能
     * 
     * @param capability 功能名称
     * @return 如果支持返回true
     */
    public boolean supportsCapability(String capability) {
        return capabilities != null && capabilities.contains(capability);
    }
    
    /**
     * 获取模型的简短描述
     * 
     * @return 简短描述
     */
    public String getShortDescription() {
        if (description != null && description.length() > 100) {
            return description.substring(0, 97) + "...";
        }
        return description;
    }
    
    /**
     * 获取成功率
     * 
     * @return 成功率百分比
     */
    public Double getSuccessRate() {
        if (usageStats == null || usageStats.getTotalRequests() == null || usageStats.getTotalRequests() == 0) {
            return null;
        }
        Long successful = usageStats.getSuccessfulRequests() != null ? usageStats.getSuccessfulRequests() : 0;
        return (successful.doubleValue() / usageStats.getTotalRequests().doubleValue()) * 100;
    }
}