package org.example.ai_agent.dto.knowledge;

import java.time.LocalDateTime;

public interface KnowledgeChunkIndexProjection {

    Long getChunkId();

    Long getDocumentId();

    Long getKnowledgeBaseId();

    Integer getChunkIndex();

    String getContent();

    String getDocumentTitle();

    String getKnowledgeBaseName();

    Long getKnowledgeBaseCreatorId();

    Boolean getKnowledgeBaseIsPublic();

    Long getDocumentUploaderId();

    String getKnowledgeBaseStatus();

    String getDocumentParseStatus();

    String getDocumentEmbeddingStatus();

    String getChunkEmbeddingStatus();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
