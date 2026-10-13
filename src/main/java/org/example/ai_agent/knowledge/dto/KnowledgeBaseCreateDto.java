package org.example.ai_agent.knowledge.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库创建请求DTO
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Data
public class KnowledgeBaseCreateDto {
    
    /**
     * 知识库名称
     */
    @Size(max = 100, message = "知识库名称长度不能超过100个字符")
    private String name;
    
    /**
     * 知识库描述
     */
    @Size(max = 1000, message = "知识库描述长度不能超过1000个字符")
    private String description;
    
    /**
     * 是否公开
     */
    private Boolean isPublic;
    
    /**
     * 使用的向量化模型
     */
    @Size(max = 120, message = "向量化模型名称长度不能超过120个字符")
    private String embeddingModel;
}
