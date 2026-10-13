package org.example.ai_agent.document.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/** Persistence row for a versioned, lease-protected document ingestion run. */
@Data
@Table("ingestion_runs")
public class IngestionRunRow implements Persistable<String> {

    @Id
    private String id;
    private Long documentId;
    private Long knowledgeBaseId;
    private Long versionId;
    private Long generation;
    private String action;
    private String currentStage;
    private String status;
    private Integer attempt;
    private String leaseToken;
    private LocalDateTime leaseUntil;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    @Transient
    private boolean newEntity;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public void markNew() {
        this.newEntity = true;
    }

    public void markPersisted() {
        this.newEntity = false;
    }
}
