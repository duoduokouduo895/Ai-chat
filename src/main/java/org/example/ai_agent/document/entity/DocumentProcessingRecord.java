package org.example.ai_agent.document.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 文档处理记录实体类
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Data
@Table("document_processing_records")
public class DocumentProcessingRecord {
    
    /**
     * 记录ID，主键
     */
    @Id
    private Long id;

    /** IngestionRun 标识；旧处理记录在兼容期保持为空。 */
    private String runId;
    
    /**
     * 文档ID
     */
    private Long documentId;
    
    /**
     * 知识库ID
     */
    private Long knowledgeBaseId;

    /** 该阶段处理的文档 generation。 */
    private Long generation;
    
    /**
     * 处理类型 (PARSE, CHUNK, EMBED)
     */
    private String processingType;
    
    /**
     * 处理状态 (PENDING, PROCESSING, COMPLETED, FAILED)
     */
    private String status;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 处理耗时（毫秒）
     */
    private Long processingTimeMs;
    
    /**
     * 重试次数
     */
    private Integer retryCount;

    /** 当前阶段 claim 的 fencing token。 */
    private String leaseToken;

    /** 当前阶段 claim 的到期时间。 */
    private LocalDateTime leaseUntil;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 错误堆栈
     */
    private String errorStack;
    
    /**
     * 处理参数（JSON格式）
     */
    private String processingParams;
    
    /**
     * 处理结果（JSON格式）
     */
    private String processingResult;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
