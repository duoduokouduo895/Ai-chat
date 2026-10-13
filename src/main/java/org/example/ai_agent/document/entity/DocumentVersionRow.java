package org.example.ai_agent.document.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/** Persistence row for one independently buildable document generation. */
@Data
@Table("document_versions")
public class DocumentVersionRow {

    @Id
    private Long id;
    private Long documentId;
    private Long generation;
    private String parseStatus;
    private String chunkStatus;
    private String embeddingStatus;
    private String activationStatus;
    private String content;
    private Integer characterCount;
    private Integer chunkCount;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime activatedAt;
}
