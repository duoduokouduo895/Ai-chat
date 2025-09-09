package org.example.ai_agent.dto.chat;

import lombok.Data;

import java.util.List;

/**
 * RAG对话请求DTO
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
@Data
public class RagChatDto {
    
    /**
     * 用户问题
     */
    private String question;
    
    /**
     * 知识库ID列表
     */
    private List<Long> knowledgeBaseIds;
    
    /**
     * 使用的AI模型ID
     */
    private String modelId;
    
    /**
     * 对话ID(可选，用于保持上下文)
     */
    private Long conversationId;
    
    /**
     * 最大检索文档数
     */
    private Integer maxRetrievedDocs = 5;
    
    /**
     * 最小相似度
     */
    private Double minSimilarity = 0.3;
}