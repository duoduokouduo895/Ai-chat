package org.example.ai_agent.document.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档块DTO
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Data
public class DocumentChunkDto {
    
    /**
     * 文档块ID
     */
    private Long id;
    
    /**
     * 所属文档ID
     */
    private Long documentId;
    
    /**
     * 所属知识库ID
     */
    private Long knowledgeBaseId;
    
    /**
     * 文档块序号
     */
    private Integer chunkIndex;
    
    /**
     * 文档块内容
     */
    private String content;
    
    /**
     * 内容字符数
     */
    private Integer characterCount;
    
    /**
     * 文档块元数据
     */
    private String metadata;
    
    /**
     * 向量ID
     */
    private String embeddingId;
    
    /**
     * 是否已向量化
     */
    private Boolean isEmbedded;
    
    /**
     * 向量化状态
     */
    private String embeddingStatus;
    
    /**
     * 向量化错误信息
     */
    private String embeddingError;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}