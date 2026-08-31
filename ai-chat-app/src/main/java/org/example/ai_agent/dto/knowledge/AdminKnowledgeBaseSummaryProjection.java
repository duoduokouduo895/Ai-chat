package org.example.ai_agent.dto.knowledge;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;

/**
 * 管理端全站知识库聚合统计。
 */
@Data
public class AdminKnowledgeBaseSummaryProjection {

    @Column("knowledge_base_count")
    private Long knowledgeBaseCount;

    @Column("document_count")
    private Long documentCount;

    @Column("public_knowledge_base_count")
    private Long publicKnowledgeBaseCount;
}
