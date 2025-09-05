package org.example.ai_agent.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 知识库文档实体类
 * <p>
 * 基于LangChain4j 1.0.1文档处理架构设计的文档管理实体
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
@Data
@Table("knowledge_documents")
public class KnowledgeDocument {
    
    /**
     * 文档ID，主键
     */
    @Id
    private Long id;
    
    /**
     * 所属知识库ID
     */
    private Long knowledgeBaseId;
    
    /**
     * 文档标题
     */
    private String title;
    
    /**
     * 原始文件名
     */
    private String fileName;
    
    /**
     * 文件类型
     */
    private String fileType;
    
    /**
     * 文件大小(字节)
     */
    private Long fileSize;
    
    /**
     * OSS存储路径
     */
    private String ossPath;
    
    /**
     * OSS访问URL
     */
    private String ossUrl;
    
    /**
     * 解析状态(PENDING:待解析, PARSING:解析中, PARSED:已解析, FAILED:解析失败)
     */
    private String parseStatus;
    
    /**
     * 解析后的文本内容
     */
    private String content;
    
    /**
     * 文档字符数
     */
    private Integer characterCount;
    
    /**
     * 文档分块数量
     */
    private Integer chunkCount;
    
    /**
     * 是否已向量化
     */
    private Boolean isEmbedded;
    
    /**
     * 向量化状态(PENDING:待向量化, PROCESSING:处理中, COMPLETED:已完成, FAILED:失败)
     */
    private String embeddingStatus;
    
    /**
     * 文档元数据(JSON格式)
     */
    private String metadata;
    
    /**
     * 上传者用户ID
     */
    private Long uploaderId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}