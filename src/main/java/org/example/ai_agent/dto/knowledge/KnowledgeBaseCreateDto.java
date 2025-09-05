package org.example.ai_agent.dto.knowledge;

import lombok.Data;

/**
 * 知识库创建请求DTO
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
@Data
public class KnowledgeBaseCreateDto {
    
    /**
     * 知识库名称
     */
    private String name;
    
    /**
     * 知识库描述
     */
    private String description;
    
    /**
     * 是否公开
     */
    private Boolean isPublic;
    
    /**
     * 使用的向量化模型
     */
    private String embeddingModel;
}