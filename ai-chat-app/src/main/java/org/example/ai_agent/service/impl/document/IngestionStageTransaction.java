package org.example.ai_agent.service.impl.document;

import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.knowledge.ingestion.StageLeaseClaim;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/** Serializes stage writes with lease takeover, activation and resource deletion. */
@Component
public class IngestionStageTransaction {

    private final DatabaseClient database;
    private final TransactionalOperator transaction;

    public IngestionStageTransaction(DatabaseClient database, TransactionalOperator transaction) {
        this.database = database;
        this.transaction = transaction;
    }

    public <T> Mono<T> execute(StageLeaseClaim claim, DocumentProcessingMessage message,
                               Supplier<Mono<T>> writes) {
        return Mono.defer(() -> {
            if (claim == null || !claim.isOwned() || claim.leaseToken() == null
                    || message == null || !claim.runId().equals(message.getRunId())
                    || !claim.requestedStage().name().equals(message.getProcessingType())) {
                return Mono.error(new IllegalArgumentException("An owned matching stage is required"));
            }
            // All commands acquire parent locks first, so deletion cannot race a child write.
            return lockKnowledgeBase(message)
                    .then(lockDocument(message))
                    .then(lockRun(claim, message))
                    .then(Mono.defer(writes))
                    .as(transaction::transactional);
        });
    }

    private Mono<Void> lockKnowledgeBase(DocumentProcessingMessage message) {
        return database.sql("""
                        SELECT id FROM knowledge_bases
                        WHERE id = :id AND deletion_status = 'ACTIVE' AND status = 'ACTIVE'
                        FOR UPDATE
                        """)
                .bind("id", message.getKnowledgeBaseId())
                .map((row, metadata) -> row.get("id", Long.class)).one()
                .switchIfEmpty(Mono.error(new IngestionLeaseLostException(message.getRunId(), "KNOWLEDGE_BASE_UNAVAILABLE")))
                .then();
    }

    private Mono<Void> lockDocument(DocumentProcessingMessage message) {
        return database.sql("""
                        SELECT id FROM knowledge_documents
                        WHERE id = :id AND knowledge_base_id = :knowledgeBaseId
                          AND deletion_status = 'ACTIVE'
                        FOR UPDATE
                        """)
                .bind("id", message.getDocumentId()).bind("knowledgeBaseId", message.getKnowledgeBaseId())
                .map((row, metadata) -> row.get("id", Long.class)).one()
                .switchIfEmpty(Mono.error(new IngestionLeaseLostException(message.getRunId(), "DOCUMENT_UNAVAILABLE")))
                .then();
    }

    private Mono<Void> lockRun(StageLeaseClaim claim, DocumentProcessingMessage message) {
        return database.sql("""
                        SELECT id FROM ingestion_runs
                        WHERE id = :id AND document_id = :documentId
                          AND knowledge_base_id = :knowledgeBaseId
                          AND version_id = :versionId AND generation = :generation
                          AND current_stage = :stage AND status = 'PROCESSING'
                          AND lease_token = :token AND lease_until > CURRENT_TIMESTAMP
                        FOR UPDATE
                        """)
                .bind("id", claim.runId()).bind("documentId", message.getDocumentId())
                .bind("knowledgeBaseId", message.getKnowledgeBaseId())
                .bind("versionId", message.getVersionId()).bind("generation", message.getGeneration())
                .bind("stage", claim.requestedStage().name()).bind("token", claim.leaseToken())
                .map((row, metadata) -> row.get("id", String.class)).one()
                .switchIfEmpty(Mono.error(new IngestionLeaseLostException(claim.runId(), "STAGE_WRITE")))
                .then();
    }
}
