package org.example.ai_agent.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库响应DTO
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Data
public class KnowledgeBaseDto {
    
    private Long id;
    private String name;
    private String description;
    private Long creatorId;
    private String creatorName;
    private Boolean isPublic;
    private String status;
    private Integer documentCount;
    private Integer vectorDimension;
    private String embeddingModel;
    private String indexName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}