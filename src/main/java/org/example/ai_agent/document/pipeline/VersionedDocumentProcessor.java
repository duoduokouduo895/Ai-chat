package org.example.ai_agent.document.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.example.ai_agent.document.dto.DocumentProcessingMessage;
import org.example.ai_agent.document.entity.DocumentProcessingRecord;
import org.example.ai_agent.knowledge.entity.KnowledgeDocumentChunk;
import org.example.ai_agent.document.domain.IngestionStage;
import org.example.ai_agent.document.domain.StageLeaseClaim;
import org.example.ai_agent.document.repository.DocumentProcessingRecordRepository;
import org.example.ai_agent.document.repository.DocumentVersionRepository;
import org.example.ai_agent.knowledge.repository.KnowledgeDocumentChunkRepository;
import org.example.ai_agent.knowledge.repository.KnowledgeDocumentRepository;
import org.example.ai_agent.document.service.DocumentParserService;
import org.example.ai_agent.knowledge.service.EmbeddingService;
import org.example.ai_agent.knowledge.service.KnowledgeChunkSearchService;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class VersionedDocumentProcessor {
    private final IngestionStageTransaction fence;
    private final IngestionRunLeaseService leases;
    private final DatabaseClient database;
    private final DocumentVersionRepository versions;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeDocumentChunkRepository chunks;
    private final DocumentProcessingStages stages;
    private final DocumentParserService parser;
    private final EmbeddingService embeddings;
    private final DocumentEmbeddingBatchProcessor batches;
    private final KnowledgeChunkSearchService search;
    private final DocumentProcessingRecordRepository records;
    private final DocumentProcessingOutboxService outbox;
    private final ObjectMapper mapper;

    public VersionedDocumentProcessor(IngestionStageTransaction fence, IngestionRunLeaseService leases,
            DatabaseClient database, DocumentVersionRepository versions, KnowledgeDocumentRepository documents,
            KnowledgeDocumentChunkRepository chunks, DocumentProcessingStages stages, DocumentParserService parser,
            EmbeddingService embeddings, DocumentEmbeddingBatchProcessor batches, KnowledgeChunkSearchService search,
            DocumentProcessingRecordRepository records, DocumentProcessingOutboxService outbox, ObjectMapper mapper) {
        this.fence = fence;
        this.leases = leases;
        this.database = database;
        this.versions = versions;
        this.documents = documents;
        this.chunks = chunks;
        this.stages = stages;
        this.parser = parser;
        this.embeddings = embeddings;
        this.batches = batches;
        this.search = search;
        this.records = records;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    public Mono<Boolean> process(StageLeaseClaim claim, DocumentProcessingMessage message) {
        return fence.execute(claim, message, () -> Mono.just(true))
                .then(Mono.defer(() -> switch (claim.requestedStage()) {
                    case PARSE -> parse(claim, message);
                    case CHUNK -> chunk(claim, message);
                    case EMBED -> embed(claim, message);
                })).thenReturn(true);
    }

    private Mono<Void> parse(StageLeaseClaim claim, DocumentProcessingMessage message) {
        return documents.findById(message.getDocumentId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Document unavailable")))
                .flatMap(document -> stages.parseDocumentFromOss(document, message.getParseMode()))
                .flatMap(content -> fence.execute(claim, message, () -> versions.findById(message.getVersionId())
                        .flatMap(version -> {
                            version.setContent(content);
                            version.setCharacterCount(content.length());
                            version.setParseStatus("COMPLETED");
                            version.setUpdatedAt(LocalDateTime.now());
                            return versions.save(version);
                        }).then(completeAndQueue(claim, message, IngestionStage.CHUNK))))
                .then();
    }

    private Mono<Void> chunk(StageLeaseClaim claim, DocumentProcessingMessage message) {
        return versions.findById(message.getVersionId())
                .filter(version -> "COMPLETED".equals(version.getParseStatus()))
                .switchIfEmpty(Mono.error(new IllegalStateException("Parsed version required")))
                .flatMap(version -> version.getContent() == null || version.getContent().isBlank()
                        ? Mono.just(List.<TextSegment>of()) : parser.splitDocument(Document.from(version.getContent())))
                .flatMap(segments -> fence.execute(claim, message, () -> chunks.findByVersionIdOrderByChunkIndex(message.getVersionId())
                        .hasElements().flatMap(exists -> exists
                                ? Mono.error(new IllegalStateException("Unexpected partial version chunks"))
                                : chunks.saveAll(IntStream.range(0, segments.size()).mapToObj(index -> {
                                    TextSegment segment = segments.get(index);
                                    KnowledgeDocumentChunk chunk = new KnowledgeDocumentChunk();
                                    chunk.setDocumentId(message.getDocumentId());
                                    chunk.setKnowledgeBaseId(message.getKnowledgeBaseId());
                                    chunk.setVersionId(message.getVersionId());
                                    chunk.setGeneration(message.getGeneration());
                                    chunk.setChunkIndex(index);
                                    chunk.setContent(segment.text());
                                    chunk.setCharacterCount(segment.text().length());
                                    chunk.setMetadata("{}");
                                    chunk.setIsEmbedded(false);
                                    chunk.setEmbeddingStatus("PENDING");
                                    chunk.setCreatedAt(LocalDateTime.now());
                                    chunk.setUpdatedAt(LocalDateTime.now());
                                    return chunk;
                                }).toList()).then())
                        .then(database.sql("""
                                UPDATE document_versions SET chunk_status = 'COMPLETED', chunk_count = :count
                                WHERE id = :versionId
                                """).bind("count", segments.size()).bind("versionId", message.getVersionId())
                                .fetch().rowsUpdated())
                        // EMBED is also the search-only finalization stage when vectorization is disabled.
                        .then(completeAndQueue(claim, message, IngestionStage.EMBED))))
                .then();
    }

    private Mono<Void> embed(StageLeaseClaim claim, DocumentProcessingMessage message) {
        Mono<Void> embedding = Boolean.FALSE.equals(message.getEnableVectorization()) ? Mono.empty()
                : chunks.findByVersionIdOrderByChunkIndex(message.getVersionId()).hasElements()
                .flatMap(hasChunks -> hasChunks ? batches.resolveEmbeddingProfile(message.getKnowledgeBaseId()).flatMap(profile ->
                    chunks.findByVersionIdOrderByChunkIndex(message.getVersionId())
                        .filter(chunk -> !"COMPLETED".equals(chunk.getEmbeddingStatus()))
                        .buffer(20).concatMap(batch -> {
                            List<TextSegment> segments = batch.stream().map(chunk -> TextSegment.from(chunk.getContent(),
                                    dev.langchain4j.data.document.Metadata.from(java.util.Map.of(
                                            "version_id", message.getVersionId(), "generation", message.getGeneration(),
                                            "chunk_index", chunk.getChunkIndex())))).toList();
                            return fence.execute(claim, message, () -> Mono.just(true))
                                    .then(embeddings.embedTexts(segments, profile.modelAlias()))
                                    .flatMap(vectors -> fence.execute(claim, message, () -> Mono.just(true))
                                        .then(Mono.defer(() -> embeddings.storeEmbeddings(message.getKnowledgeBaseId(),
                                            message.getDocumentId(), batch.stream().map(KnowledgeDocumentChunk::getId).toList(),
                                            vectors, segments, profile.modelAlias(), profile.indexName()))))
                                    .flatMap(ids -> {
                                        if (ids.size() != batch.size()) return Mono.error(new IllegalStateException("Embedding count mismatch"));
                                        return fence.execute(claim, message, () -> Flux.range(0, batch.size()).concatMap(index -> {
                                            KnowledgeDocumentChunk chunk = batch.get(index);
                                            chunk.setEmbeddingId(ids.get(index));
                                            chunk.setIsEmbedded(true);
                                            chunk.setEmbeddingStatus("COMPLETED");
                                            chunk.setUpdatedAt(LocalDateTime.now());
                                            return chunks.save(chunk);
                                        }).then());
                                    });
                        }).then()) : Mono.empty());
        return embedding.then(Mono.defer(() -> validateChunks(message)))
                .then(Mono.defer(() -> Boolean.FALSE.equals(message.getEnableVectorization()) ? Mono.empty()
                        : chunks.findByVersionIdOrderByChunkIndex(message.getVersionId()).hasElements()
                            .flatMap(hasChunks -> hasChunks ? batches.refreshEmbeddingIndex(message.getKnowledgeBaseId()) : Mono.empty())))
                .then(search.indexVersionChunks(message.getDocumentId(), message.getVersionId()))
                .then(fence.execute(claim, message, () -> activate(message)
                        .then(completeAndQueue(claim, message, null))));
    }

    private Mono<Void> validateChunks(DocumentProcessingMessage message) {
        return versions.findById(message.getVersionId()).flatMap(version ->
                chunks.findByVersionIdOrderByChunkIndex(message.getVersionId()).collectList().flatMap(rows -> {
                    boolean valid = "COMPLETED".equals(version.getChunkStatus())
                            && rows.size() == version.getChunkCount()
                            && (!rows.isEmpty() || version.getContent() == null || version.getContent().isBlank())
                            && (Boolean.FALSE.equals(message.getEnableVectorization()) || rows.stream().allMatch(row ->
                                "COMPLETED".equals(row.getEmbeddingStatus()) && row.getEmbeddingId() != null && !row.getEmbeddingId().isBlank()));
                    return valid ? Mono.empty() : Mono.error(new IllegalStateException("Version chunks are incomplete"));
                }));
    }

    private Mono<Void> activate(DocumentProcessingMessage message) {
        return database.sql("""
                        INSERT IGNORE INTO asset_cleanup_tasks (id, resource_type, resource_id, owner_id)
                        SELECT UUID(), 'VERSION', v.id, k.creator_id FROM document_versions v
                        INNER JOIN knowledge_documents d ON d.id = v.document_id
                        INNER JOIN knowledge_bases k ON k.id = d.knowledge_base_id
                        WHERE d.id = :documentId AND v.activation_status = 'ACTIVE' AND v.id <> :versionId
                        """).bind("documentId", message.getDocumentId()).bind("versionId", message.getVersionId())
                .fetch().rowsUpdated()
                .then(database.sql("""
                        UPDATE document_versions SET activation_status = 'RETIRED'
                        WHERE document_id = :documentId AND activation_status = 'ACTIVE' AND id <> :versionId
                        """).bind("documentId", message.getDocumentId()).bind("versionId", message.getVersionId()).fetch().rowsUpdated())
                .then(database.sql("""
                        UPDATE document_versions SET activation_status = 'ACTIVE',
                            embedding_status = CASE WHEN chunk_count = 0 THEN 'SKIPPED' ELSE :status END,
                            activated_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = :versionId
                        """).bind("status", Boolean.FALSE.equals(message.getEnableVectorization()) ? "SKIPPED" : "COMPLETED")
                        .bind("versionId", message.getVersionId()).fetch().rowsUpdated())
                .then(database.sql("""
                        UPDATE knowledge_documents d INNER JOIN document_versions v ON v.document_id = d.id
                        SET d.active_version_id = v.id, d.content = v.content, d.character_count = v.character_count,
                            d.chunk_count = v.chunk_count, d.parse_status = v.parse_status,
                            d.embedding_status = v.embedding_status, d.is_embedded = (v.chunk_count > 0 AND v.embedding_status = 'COMPLETED'),
                            d.updated_at = CURRENT_TIMESTAMP
                        WHERE d.id = :documentId AND v.id = :versionId
                        """).bind("documentId", message.getDocumentId()).bind("versionId", message.getVersionId()).fetch().rowsUpdated()).then();
    }

    private Mono<Void> completeAndQueue(StageLeaseClaim claim, DocumentProcessingMessage message, IngestionStage next) {
        return database.sql("""
                        UPDATE document_processing_records SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP,
                            end_time = CURRENT_TIMESTAMP WHERE run_id = :runId AND processing_type = :stage
                        """).bind("runId", message.getRunId()).bind("stage", message.getProcessingType()).fetch().rowsUpdated()
                .filter(count -> count == 1).switchIfEmpty(Mono.error(new IllegalStateException("Stage record missing")))
                .then(next == null ? Mono.empty() : Mono.defer(() -> {
                    DocumentProcessingMessage nextMessage = mapper.convertValue(message, DocumentProcessingMessage.class);
                    nextMessage.setProcessingType(next.name());
                    nextMessage.setRetryCount(0);
                    return createRecord(nextMessage).then(enqueue(nextMessage, "document." + next.name().toLowerCase(java.util.Locale.ROOT)));
                })).then(leases.completeStage(claim, next));
    }

    private Mono<Void> createRecord(DocumentProcessingMessage message) {
        return Mono.fromCallable(() -> {
            DocumentProcessingRecord record = new DocumentProcessingRecord();
            record.setRunId(message.getRunId()); record.setGeneration(message.getGeneration());
            record.setDocumentId(message.getDocumentId()); record.setKnowledgeBaseId(message.getKnowledgeBaseId());
            record.setProcessingType(message.getProcessingType()); record.setStatus("PENDING"); record.setRetryCount(0);
            record.setProcessingParams(mapper.writeValueAsString(message));
            record.setCreatedAt(LocalDateTime.now()); record.setUpdatedAt(LocalDateTime.now());
            return record;
        }).flatMap(records::save).then();
    }

    private Mono<Void> enqueue(DocumentProcessingMessage message, String routingKey) {
        return outbox.enqueue(message, routingKey).filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(new IllegalStateException("Stage event was not persisted"))).then();
    }

    public Mono<Void> retry(StageLeaseClaim claim, DocumentProcessingMessage message, String routingKey) {
        return fence.execute(claim, message, () -> database.sql("""
                        UPDATE document_processing_records SET retry_count = :attempt, status = 'PENDING', updated_at = CURRENT_TIMESTAMP
                        WHERE run_id = :runId AND processing_type = :stage
                        """).bind("attempt", message.getRetryCount()).bind("runId", message.getRunId())
                .bind("stage", message.getProcessingType()).fetch().rowsUpdated()
                .filter(count -> count == 1).switchIfEmpty(Mono.error(new IllegalStateException("Stage record missing")))
                .then(enqueue(message, routingKey))
                .then(leases.releaseForRetry(claim, "RETRYABLE_STAGE_FAILURE", "Stage failed; retry accepted")));
    }

    public Mono<Void> fail(StageLeaseClaim claim, DocumentProcessingMessage message) {
        return fence.execute(claim, message, () -> database.sql("""
                        UPDATE document_processing_records SET status = 'FAILED', updated_at = CURRENT_TIMESTAMP
                        WHERE run_id = :runId AND processing_type = :stage
                        """).bind("runId", message.getRunId()).bind("stage", message.getProcessingType()).fetch().rowsUpdated()
                .then(database.sql("UPDATE document_versions SET activation_status = 'FAILED' WHERE id = :id")
                        .bind("id", message.getVersionId()).fetch().rowsUpdated())
                .then(leases.fail(claim, "PERMANENT_STAGE_FAILURE", "Version build failed")));
    }
}
