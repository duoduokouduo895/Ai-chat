package org.example.ai_agent.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 知识库实体类
 * <p>
 * 基于LangChain4j 1.0.1文档处理架构设计的知识库管理实体
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Data
@Table("knowledge_bases")
public class KnowledgeBase {
    private String deletionStatus = "ACTIVE";
    
    /**
     * 知识库ID，主键
     */
    @Id
    private Long id;
    
    /**
     * 知识库名称
     */
    private String name;
    
    /**
     * 知识库描述
     */
    private String description;
    
    /**
     * 创建者用户ID
     */
    private Long creatorId;
    
    /**
     * 是否公开(true:公开, false:私有)
     */
    private Boolean isPublic;
    
    /**
     * 知识库状态(ACTIVE:活跃, ARCHIVED:归档, DELETED:已删除)
     */
    private String status;
    
    /**
     * 文档数量
     */
    private Integer documentCount;
    
    /**
     * 知识库向量维度
     */
    private Integer vectorDimension;
    
    /**
     * 使用的向量化模型
     */
    private String embeddingModel;
    
    /**
     * Elasticsearch索引名称
     */
    private String indexName;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}