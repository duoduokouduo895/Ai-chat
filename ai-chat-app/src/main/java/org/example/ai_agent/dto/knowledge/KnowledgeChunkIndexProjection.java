package org.example.ai_agent.dto.knowledge;

import lombok.Data;
import org.springframework.data.relational.core.mapping.Column;

import java.time.LocalDateTime;

/**
 * 知识分块检索索引的数据库行映射。
 *
 * <p>这里必须使用带显式 {@link Column} 的具体类型。R2DBC 对原接口投影的
 * snake_case 别名不会稳定转换为 camelCase getter，曾导致 {@code chunk_id}
 * 已由数据库返回，但 {@code getChunkId()} 仍为 {@code null}。</p>
 */
@Data
public class KnowledgeChunkIndexProjection {

    @Column("chunk_id")
    private Long chunkId;

    @Column("document_id")
    private Long documentId;

    @Column("knowledge_base_id")
    private Long knowledgeBaseId;

    @Column("chunk_index")
    private Integer chunkIndex;

    private String content;

    @Column("document_title")
    private String documentTitle;

    @Column("knowledge_base_name")
    private String knowledgeBaseName;

    @Column("knowledge_base_creator_id")
    private Long knowledgeBaseCreatorId;

    @Column("knowledge_base_is_public")
    private Boolean knowledgeBaseIsPublic;

    @Column("document_uploader_id")
    private Long documentUploaderId;

    @Column("knowledge_base_status")
    private String knowledgeBaseStatus;

    @Column("document_parse_status")
    private String documentParseStatus;

    @Column("document_embedding_status")
    private String documentEmbeddingStatus;

    @Column("chunk_embedding_status")
    private String chunkEmbeddingStatus;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
