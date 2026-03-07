package org.example.ai_agent.service;

import org.example.ai_agent.dto.ai.ModelInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI模型管理服务接口
 * <p>
 * 提供AI模型的查询、管理和配置功能。
 * 支持动态模型切换、模型状态监控等功能。
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
public interface ModelManagementService {
    
    /**
     * 获取所有可用的AI模型列表
     * 
     * @return 模型信息流
     */
    Flux<ModelInfo> getAllModels();
    
    /**
     * 根据模型ID获取模型信息
     * 
     * @param modelId 模型ID
     * @return 模型信息
     */
    Mono<ModelInfo> getModelById(String modelId);
    
    /**
     * 获取默认模型信息
     * 
     * @return 默认模型信息
     */
    Mono<ModelInfo> getDefaultModel();
    
    /**
     * 检查模型是否可用
     * 
     * @param modelId 模型ID
     * @return 是否可用
     */
    Mono<Boolean> isModelAvailable(String modelId);
    
    /**
     * 获取模型的健康状态
     * 
     * @param modelId 模型ID
     * @return 健康状态信息
     */
    Mono<String> getModelHealth(String modelId);
    
    /**
     * 刷新模型配置
     * 从配置源重新加载模型配置
     * 
     * @return 刷新结果
     */
    Mono<String> refreshModelConfigurations();
    
    /**
     * 获取模型使用统计信息
     * 
     * @param modelId 模型ID
     * @return 使用统计信息
     */
    Mono<ModelInfo.ModelUsageStats> getModelUsageStats(String modelId);
}