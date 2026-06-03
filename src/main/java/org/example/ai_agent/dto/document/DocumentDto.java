package org.example.ai_agent.dto.document;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档响应DTO
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Data
public class DocumentDto {
    
    private Long id;
    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private String title;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String ossPath;
    private String ossUrl;
    private String parseStatus;
    private String content;
    private Integer characterCount;
    private Integer chunkCount;
    private Boolean isEmbedded;
    private String embeddingStatus;
    private String metadata;
    private Long uploaderId;
    private String uploaderName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}