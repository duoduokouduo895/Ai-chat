package org.example.ai_agent.document.dto;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

/**
 * 文档列表只读投影。
 *
 * 列表页不需要传输解析正文，关联名称由 SQL JOIN 一次性补齐。
 */
@Data
public class DocumentListProjection {

    private Long id;

    @Column("knowledge_base_id")
    private Long knowledgeBaseId;

    @Column("knowledge_base_name")
    private String knowledgeBaseName;

    private String title;

    @Column("file_name")
    private String fileName;

    @Column("file_type")
    private String fileType;

    @Column("file_size")
    private Long fileSize;

    @Column("oss_path")
    private String ossPath;

    @Column("oss_url")
    private String ossUrl;

    @Column("parse_status")
    private String parseStatus;

    @Column("character_count")
    private Integer characterCount;

    @Column("chunk_count")
    private Integer chunkCount;

    @Column("is_embedded")
    private Boolean isEmbedded;

    @Column("embedding_status")
    private String embeddingStatus;

    private String metadata;

    @Column("uploader_id")
    private Long uploaderId;

    @Column("uploader_name")
    private String uploaderName;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
