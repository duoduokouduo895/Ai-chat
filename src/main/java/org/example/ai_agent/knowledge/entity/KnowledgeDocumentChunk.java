package org.example.ai_agent.knowledge.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 文档块实体类
 * <p>
 * 对应LangChain4j的TextSegment，存储文档分割后的文本块和向量信息
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Data
@Table("knowledge_document_chunks")
public class KnowledgeDocumentChunk {
    
    /**
     * 文档块ID，主键
     */
    @Id
    private Long id;
    
    /**
     * 所属文档ID
     */
    private Long documentId;

    /** 所属 DocumentVersion；迁移期允许旧写入暂时为空。 */
    private Long versionId;

    /** 文档处理 generation；迁移期允许旧写入暂时为空。 */
    private Long generation;
    
    /**
     * 所属知识库ID
     */
    private Long knowledgeBaseId;
    
    /**
     * 文档块序号(在文档中的位置)
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
     * 文档块元数据(JSON格式)
     */
    private String metadata;
    
    /**
     * 向量ID(在Elasticsearch中的ID)
     */
    private String embeddingId;
    
    /**
     * 是否已向量化
     */
    private Boolean isEmbedded;
    
    /**
     * 向量化状态 (PENDING:待处理, PROCESSING:处理中, COMPLETED:已完成, FAILED:失败)
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
