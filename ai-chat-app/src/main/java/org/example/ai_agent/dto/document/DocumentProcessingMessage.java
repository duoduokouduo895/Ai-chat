package org.example.ai_agent.dto.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.ai_agent.constants.DocumentProcessingType;

import java.time.LocalDateTime;

/**
 * 文档处理消息DTO
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentProcessingMessage {
    
    private Long documentId;
    private Long knowledgeBaseId;
    private String runId;
    private Long versionId;
    private Long generation;
    private String processingType;
    private String parseMode;
    private Boolean enableVectorization;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private String errorMessage;
    private String extraParams;

    public DocumentProcessingType processingType() {
        return DocumentProcessingType.fromValue(processingType);
    }
    
    /**
     * 创建解析消息
     */
    public static DocumentProcessingMessage createParseMessage(Long documentId, Long knowledgeBaseId, 
                                                             String parseMode, Boolean enableVectorization) {
        DocumentProcessingMessage message = new DocumentProcessingMessage();
        message.setDocumentId(documentId);
        message.setKnowledgeBaseId(knowledgeBaseId);
        message.setProcessingType(DocumentProcessingType.PARSE.name());
        message.setParseMode(parseMode);
        message.setEnableVectorization(enableVectorization);
        message.setRetryCount(0);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }
    
    /**
     * 创建分块消息（带向量化选项）
     */
    public static DocumentProcessingMessage createChunkMessage(Long documentId, Long knowledgeBaseId, Boolean enableVectorization) {
        DocumentProcessingMessage message = new DocumentProcessingMessage();
        message.setDocumentId(documentId);
        message.setKnowledgeBaseId(knowledgeBaseId);
        message.setProcessingType(DocumentProcessingType.CHUNK.name());
        message.setEnableVectorization(enableVectorization);
        message.setRetryCount(0);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }
    
    /**
     * 创建向量化消息
     */
    public static DocumentProcessingMessage createEmbedMessage(Long documentId, Long knowledgeBaseId) {
        DocumentProcessingMessage message = new DocumentProcessingMessage();
        message.setDocumentId(documentId);
        message.setKnowledgeBaseId(knowledgeBaseId);
        message.setProcessingType(DocumentProcessingType.EMBED.name());
        message.setRetryCount(0);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }
    
    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        this.retryCount = this.retryCount == null ? 1 : this.retryCount + 1;
    }
}
