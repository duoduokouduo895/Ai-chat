package org.example.ai_agent.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 文档处理 transactional outbox 事件。
 * 消息先随业务状态写入数据库，再由投递器发送到 RabbitMQ，保证至少一次投递。
 */
@Data
@Table("document_processing_outbox")
public class DocumentProcessingOutboxEvent {

    @Id
    private Long id;
    private String runId;
    private String eventKey;
    private Long documentId;
    private Long knowledgeBaseId;
    private Long versionId;
    private Long generation;
    private String processingType;
    private String routingKey;
    private String payload;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime publishedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
