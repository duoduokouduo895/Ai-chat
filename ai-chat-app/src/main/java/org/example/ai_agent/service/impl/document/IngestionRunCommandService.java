package org.example.ai_agent.service.impl.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.constants.DocumentProcessingStatus;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.entity.DocumentProcessingRecord;
import org.example.ai_agent.entity.DocumentVersionRow;
import org.example.ai_agent.entity.IngestionRunRow;
import org.example.ai_agent.knowledge.ingestion.AcceptedIngestionRun;
import org.example.ai_agent.knowledge.ingestion.IngestionStage;
import org.example.ai_agent.knowledge.ingestion.IngestionAction;
import org.example.ai_agent.repository.document.DocumentProcessingRecordRepository;
import org.example.ai_agent.repository.document.DocumentVersionRepository;
import org.example.ai_agent.repository.document.IngestionRunRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class IngestionRunCommandService {

    private static final List<String> ACTIVE_RUN_STATUSES = List.of("PENDING", "PROCESSING", "RETRY");

    private final DatabaseClient databaseClient;
    private final DocumentVersionRepository versionRepository;
    private final IngestionRunRepository runRepository;
    private final DocumentProcessingRecordRepository recordRepository;
    private final DocumentProcessingOutboxService outboxService;
    private final TransactionalOperator transactionalOperator;
    private final ObjectMapper objectMapper;

    public IngestionRunCommandService(DatabaseClient databaseClient,
                                      DocumentVersionRepository versionRepository,
                                      IngestionRunRepository runRepository,
                                      DocumentProcessingRecordRepository recordRepository,
                                      DocumentProcessingOutboxService outboxService,
                                      TransactionalOperator transactionalOperator,
                                      ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.versionRepository = versionRepository;
        this.runRepository = runRepository;
        this.recordRepository = recordRepository;
        this.outboxService = outboxService;
        this.transactionalOperator = transactionalOperator;
        this.objectMapper = objectMapper;
    }

    public Mono<AcceptedIngestionRun> acceptInitialIngestion(Long documentId,
                                                             Long knowledgeBaseId,
                                                             String parseMode,
                                                             Boolean enableVectorization) {
        return accept(documentId, knowledgeBaseId, IngestionAction.START_INGESTION, parseMode, enableVectorization);
    }

    public Mono<AcceptedIngestionRun> accept(Long documentId, Long knowledgeBaseId,
                                             IngestionAction action, String parseMode,
                                             Boolean enableVectorization) {
        validateIds(documentId, knowledgeBaseId);
        if (action == null) {
            return Mono.error(new IllegalArgumentException("Ingestion action is required"));
        }
        return lockDocument(documentId, knowledgeBaseId)
                .then(requireLegacyDrained(documentId))
                .then(snapshotLegacy(documentId))
                .then(runRepository.findFirstByDocumentIdAndStatusInOrderByGenerationDesc(
                        documentId, ACTIVE_RUN_STATUSES))
                .flatMap(run -> action.name().equals(run.getAction())
                        ? Mono.just(existingAcceptance(run))
                        : Mono.error(new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.CONFLICT, "INGESTION_ACTION_CONFLICT")))
                .switchIfEmpty(Mono.defer(() -> allocateGeneration(documentId)
                        .flatMap(generation -> createRun(
                                documentId, knowledgeBaseId, generation,
                                action, parseMode, enableVectorization))))
                .as(transactionalOperator::transactional);
    }

    private Mono<AcceptedIngestionRun> createRun(Long documentId,
                                                  Long knowledgeBaseId,
                                                  Long generation,
                                                  IngestionAction action,
                                                  String parseMode,
                                                  Boolean enableVectorization) {
        LocalDateTime now = LocalDateTime.now();
        DocumentVersionRow version = new DocumentVersionRow();
        version.setDocumentId(documentId);
        version.setGeneration(generation);
        version.setParseStatus(DocumentProcessingStatus.PENDING.value());
        version.setChunkStatus(DocumentProcessingStatus.PENDING.value());
        version.setEmbeddingStatus(DocumentProcessingStatus.PENDING.value());
        version.setActivationStatus("BUILDING");
        version.setCharacterCount(0);
        version.setChunkCount(0);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);

        return versionRepository.save(version)
                .flatMap(savedVersion -> (action == IngestionAction.REEMBED
                        ? copyActiveVersion(savedVersion) : Mono.<Void>empty())
                        .then(Mono.defer(() -> persistRunAndFirstStage(
                                savedVersion, knowledgeBaseId, action, parseMode, enableVectorization, now))));
    }

    private Mono<Void> copyActiveVersion(DocumentVersionRow target) {
        return databaseClient.sql("""
                        UPDATE document_versions target
                        INNER JOIN knowledge_documents d ON d.id = target.document_id
                        INNER JOIN document_versions source ON source.id = d.active_version_id
                        SET target.content = source.content, target.metadata = source.metadata,
                            target.character_count = source.character_count, target.chunk_count = source.chunk_count,
                            target.parse_status = 'COMPLETED', target.chunk_status = 'COMPLETED'
                        WHERE target.id = :versionId AND source.parse_status = 'COMPLETED'
                          AND source.activation_status = 'ACTIVE' AND source.id <> target.id
                        """)
                .bind("versionId", target.getId()).fetch().rowsUpdated()
                .filter(count -> count == 1)
                .switchIfEmpty(Mono.error(new IllegalStateException("REEMBED_REQUIRES_ACTIVE_PARSED_VERSION")))
                .then(databaseClient.sql("""
                        INSERT INTO knowledge_document_chunks
                            (document_id, version_id, generation, knowledge_base_id, chunk_index, content,
                             character_count, metadata, is_embedded, embedding_status, created_at, updated_at)
                        SELECT c.document_id, :versionId, :generation, c.knowledge_base_id, c.chunk_index,
                               c.content, c.character_count, c.metadata, false, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        FROM knowledge_document_chunks c
                        INNER JOIN knowledge_documents d ON d.id = c.document_id AND d.active_version_id = c.version_id
                        WHERE d.id = :documentId
                        """)
                        .bind("versionId", target.getId()).bind("generation", target.getGeneration())
                        .bind("documentId", target.getDocumentId()).fetch().rowsUpdated())
                .then();
    }

    private Mono<AcceptedIngestionRun> persistRunAndFirstStage(DocumentVersionRow version,
                                                               Long knowledgeBaseId,
                                                               IngestionAction action,
                                                               String parseMode,
                                                               Boolean enableVectorization,
                                                               LocalDateTime now) {
        String runId = UUID.randomUUID().toString();
        IngestionRunRow run = new IngestionRunRow();
        run.setId(runId);
        run.setDocumentId(version.getDocumentId());
        run.setKnowledgeBaseId(knowledgeBaseId);
        run.setVersionId(version.getId());
        run.setGeneration(version.getGeneration());
        run.setAction(action.name());
        IngestionStage firstStage = action == IngestionAction.REEMBED ? IngestionStage.EMBED : IngestionStage.PARSE;
        run.setCurrentStage(firstStage.name());
        run.setStatus("PENDING");
        run.setAttempt(0);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        run.markNew();

        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(
                version.getDocumentId(), knowledgeBaseId, parseMode, enableVectorization);
        message.setRunId(runId);
        message.setVersionId(version.getId());
        message.setGeneration(version.getGeneration());
        message.setProcessingType(firstStage.name());

        return runRepository.save(run)
                .then(createFirstStageRecord(message, now))
                .then(outboxService.enqueue(
                        message, firstStage == IngestionStage.EMBED
                                ? DocumentProcessingConstants.RoutingKey.DOCUMENT_EMBED
                                : DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE))
                .flatMap(enqueued -> Boolean.TRUE.equals(enqueued)
                        ? Mono.just(new AcceptedIngestionRun(
                                runId,
                                version.getDocumentId(),
                                knowledgeBaseId,
                                version.getId(),
                                version.getGeneration(),
                                firstStage,
                                true))
                        : Mono.error(new IllegalStateException(
                                "Initial ingestion event was not persisted")));
    }

    private Mono<DocumentProcessingRecord> createFirstStageRecord(DocumentProcessingMessage message,
                                                                   LocalDateTime now) {
        return Mono.fromCallable(() -> {
                    DocumentProcessingRecord record = new DocumentProcessingRecord();
                    record.setRunId(message.getRunId());
                    record.setDocumentId(message.getDocumentId());
                    record.setKnowledgeBaseId(message.getKnowledgeBaseId());
                    record.setGeneration(message.getGeneration());
                    record.setProcessingType(message.getProcessingType());
                    record.setStatus(DocumentProcessingStatus.PENDING.value());
                    record.setRetryCount(0);
                    record.setProcessingParams(serialize(message));
                    record.setCreatedAt(now);
                    record.setUpdatedAt(now);
                    return record;
                })
                .flatMap(recordRepository::save);
    }

    private Mono<Void> lockDocument(Long documentId, Long knowledgeBaseId) {
        return databaseClient.sql("""
                        SELECT id FROM knowledge_bases
                        WHERE id = :knowledgeBaseId AND deletion_status = 'ACTIVE' AND status = 'ACTIVE'
                        FOR UPDATE
                        """)
                .bind("knowledgeBaseId", knowledgeBaseId)
                .map((row, metadata) -> row.get("id", Long.class)).one()
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Knowledge base is unavailable")))
                .then(databaseClient.sql("""
                        SELECT id
                        FROM knowledge_documents
                        WHERE id = :documentId AND knowledge_base_id = :knowledgeBaseId
                          AND deletion_status = 'ACTIVE'
                        FOR UPDATE
                        """)
                .bind("documentId", documentId)
                .bind("knowledgeBaseId", knowledgeBaseId)
                .map((row, metadata) -> row.get("id", Long.class))
                .one()
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Document does not exist in the requested knowledge base: " + documentId)))
                .then());
    }

    private Mono<Long> allocateGeneration(Long documentId) {
        return databaseClient.sql("""
                        SELECT COALESCE(MAX(generation), 0) + 1 AS next_generation
                        FROM document_versions
                        WHERE document_id = :documentId
                        """)
                .bind("documentId", documentId)
                .map((row, metadata) -> row.get("next_generation", Long.class))
                .one()
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Unable to allocate document generation")));
    }

    private Mono<Void> snapshotLegacy(Long documentId) {
        return databaseClient.sql("""
                        INSERT INTO document_versions (document_id, generation, parse_status, chunk_status,
                            embedding_status, activation_status, content, character_count, chunk_count, metadata, activated_at)
                        SELECT d.id, 1, d.parse_status, 'COMPLETED', d.embedding_status, 'ACTIVE', d.content,
                               d.character_count, d.chunk_count, d.metadata, CURRENT_TIMESTAMP
                        FROM knowledge_documents d WHERE d.id = :id AND d.parse_status = 'COMPLETED'
                          AND NOT EXISTS (SELECT 1 FROM document_versions v WHERE v.document_id = d.id)
                        """).bind("id", documentId).fetch().rowsUpdated()
                .then(databaseClient.sql("""
                        UPDATE knowledge_documents d INNER JOIN document_versions v ON v.document_id = d.id AND v.generation = 1
                        SET d.active_version_id = v.id, v.content = d.content, v.character_count = d.character_count,
                            v.chunk_count = d.chunk_count, v.metadata = d.metadata, v.parse_status = d.parse_status,
                            v.chunk_status = IF(d.parse_status = 'COMPLETED', 'COMPLETED', 'PENDING'),
                            v.embedding_status = d.embedding_status
                        WHERE d.id = :id AND NOT EXISTS (SELECT 1 FROM ingestion_runs r WHERE r.document_id = d.id)
                        """).bind("id", documentId).fetch().rowsUpdated())
                .then(databaseClient.sql("""
                        UPDATE knowledge_document_chunks c INNER JOIN knowledge_documents d ON d.id = c.document_id
                        SET c.version_id = d.active_version_id
                        WHERE d.id = :id AND c.version_id IS NULL AND c.generation = 1
                          AND NOT EXISTS (SELECT 1 FROM ingestion_runs r WHERE r.document_id = d.id)
                        """).bind("id", documentId).fetch().rowsUpdated()).then();
    }

    private Mono<Void> requireLegacyDrained(Long documentId) {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS active_count FROM document_processing_records
                        WHERE document_id = :id AND run_id IS NULL AND status IN ('PENDING', 'PROCESSING', 'RETRY')
                        """).bind("id", documentId).map((row, metadata) -> row.get("active_count", Long.class)).one()
                .flatMap(count -> count == 0 ? Mono.empty()
                        : Mono.error(new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.CONFLICT, "LEGACY_INGESTION_IN_FLIGHT")));
    }

    public Mono<Void> requireLegacyWritable(Long documentId) {
        return databaseClient.sql("""
                        SELECT d.id FROM knowledge_documents d INNER JOIN knowledge_bases k ON k.id = d.knowledge_base_id
                        WHERE d.id = :id AND d.deletion_status = 'ACTIVE' AND k.deletion_status = 'ACTIVE'
                          AND NOT EXISTS (SELECT 1 FROM ingestion_runs r WHERE r.document_id = d.id)
                          AND NOT EXISTS (SELECT 1 FROM document_versions v WHERE v.document_id = d.id AND v.generation > 1)
                        """).bind("id", documentId).map((row, metadata) -> row.get("id", Long.class)).one()
                .switchIfEmpty(Mono.error(new IllegalStateException("LEGACY_INGESTION_FORBIDDEN"))).then();
    }

    private AcceptedIngestionRun existingAcceptance(IngestionRunRow run) {
        return new AcceptedIngestionRun(
                run.getId(),
                run.getDocumentId(),
                run.getKnowledgeBaseId(),
                run.getVersionId(),
                run.getGeneration(),
                IngestionStage.fromValue(run.getCurrentStage()),
                false);
    }

    private String serialize(DocumentProcessingMessage message) throws JsonProcessingException {
        return objectMapper.writeValueAsString(message);
    }

    private void validateIds(Long documentId, Long knowledgeBaseId) {
        if (documentId == null || documentId <= 0
                || knowledgeBaseId == null || knowledgeBaseId <= 0) {
            throw new IllegalArgumentException("Document and knowledge base IDs must be positive");
        }
    }
}
