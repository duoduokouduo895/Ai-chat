package org.example.ai_agent.dto.document;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;

/**
 * 管理端全站文档聚合统计。
 */
@Data
public class AdminDocumentSummaryProjection {

    @Column("document_count")
    private Long documentCount;

    @Column("uploader_count")
    private Long uploaderCount;

    @Column("total_file_size")
    private Long totalFileSize;
}
