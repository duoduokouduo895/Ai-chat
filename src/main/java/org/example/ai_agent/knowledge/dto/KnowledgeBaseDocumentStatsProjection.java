package org.example.ai_agent.knowledge.dto;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;

/**
 * 知识库文档状态聚合投影。
 */
@Data
public class KnowledgeBaseDocumentStatsProjection {

    @Column("document_count")
    private Long documentCount;

    @Column("parsed_document_count")
    private Long parsedDocumentCount;

    @Column("failed_document_count")
    private Long failedDocumentCount;

    @Column("embedded_document_count")
    private Long embeddedDocumentCount;
}
