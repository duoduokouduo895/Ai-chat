package org.example.ai_agent.service.impl.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.r2dbc.spi.ConnectionFactory;
import org.example.ai_agent.config.properties.DocumentProcessingProperties;
import org.example.ai_agent.knowledge.ingestion.AcceptedIngestionRun;
import org.example.ai_agent.repository.document.DocumentProcessingOutboxRepository;
import org.example.ai_agent.repository.document.DocumentProcessingRecordRepository;
import org.example.ai_agent.repository.document.DocumentVersionRepository;
import org.example.ai_agent.repository.document.IngestionRunRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Timeout(value = 180)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "AI_CHAT_MYSQL_IT_ENABLED", matches = "true")
class IngestionRunCommandMySqlIntegrationTest {

    private static final long KNOWLEDGE_BASE_ID = 9320001L;
    private static final long DOCUMENT_ID = 9320002L;

    private DatabaseClient databaseClient;
    private org.example.ai_agent.repository.knowledge.KnowledgeDocumentChunkRepository chunks;
    private IngestionRunCommandService service;
    private IngestionRunCommandService failingOutboxService;
    private IngestionRunLeaseService leases;
    private IngestionStageTransaction fence;
    private VersionedDocumentProcessor processor;
    private org.example.ai_agent.service.KnowledgeChunkSearchService search;
    private org.example.ai_agent.service.impl.knowledge.KnowledgeCleanupCommands cleanup;
    private org.example.ai_agent.service.impl.knowledge.KnowledgeCleanupWorker cleanupWorker;
    private org.example.ai_agent.service.EmbeddingService cleanupEmbeddings;
    private org.example.ai_agent.service.OssService cleanupOss;
    private DocumentProcessingStages stages;
    private org.example.ai_agent.service.DocumentParserService parser;
    private org.example.ai_agent.service.EmbeddingService versionEmbeddings;
    private DocumentEmbeddingBatchProcessor embeddingBatches;

    @BeforeAll
    void setUpDatabase() {
        MySqlTestSettings settings = MySqlTestSettings.fromEnvironment();
        settings.requireIsolatedDatabase();
        Flyway.configure()
                .dataSource(settings.jdbcUrl(), settings.username(), settings.password())
                .locations("classpath:db/migration")
                .encoding("UTF-8")
                .outOfOrder(true)
                .load()
                .migrate();

        ConnectionFactory connectionFactory = MySqlConnectionFactory.from(
                MySqlConnectionConfiguration.builder()
                        .host(settings.host())
                        .port(settings.port())
                        .database(settings.database())
                        .username(settings.username())
                        .password(settings.password())
                        .build());
        databaseClient = DatabaseClient.create(connectionFactory);
        R2dbcRepositoryFactory repositories = new R2dbcRepositoryFactory(
                new R2dbcEntityTemplate(connectionFactory));
        chunks = repositories.getRepository(org.example.ai_agent.repository.knowledge.KnowledgeDocumentChunkRepository.class);
        DocumentVersionRepository versionRepository = repositories.getRepository(DocumentVersionRepository.class);
        IngestionRunRepository runRepository = repositories.getRepository(IngestionRunRepository.class);
        DocumentProcessingRecordRepository recordRepository = repositories.getRepository(
                DocumentProcessingRecordRepository.class);
        DocumentProcessingOutboxRepository outboxRepository = repositories.getRepository(
                DocumentProcessingOutboxRepository.class);
        TransactionalOperator transaction = TransactionalOperator.create(
                new R2dbcTransactionManager(connectionFactory));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        leases = new IngestionRunLeaseService(runRepository, new DocumentProcessingProperties());
        fence = new IngestionStageTransaction(databaseClient, transaction);

        DocumentProcessingOutboxService realOutbox = new DocumentProcessingOutboxService(
                outboxRepository,
                mock(DocumentProcessingPublisher.class),
                objectMapper,
                new DocumentProcessingProperties(),
                new SimpleMeterRegistry());
        service = new IngestionRunCommandService(
                databaseClient, versionRepository, runRepository, recordRepository,
                realOutbox, transaction, objectMapper);
        stages = mock(DocumentProcessingStages.class);
        when(stages.parseDocumentFromOss(any(), anyString())).thenReturn(Mono.just("version content"));
        parser = mock(org.example.ai_agent.service.DocumentParserService.class);
        when(parser.splitDocument(any())).thenReturn(Mono.just(List.of(
                dev.langchain4j.data.segment.TextSegment.from("version content"))));
        search = mock(org.example.ai_agent.service.KnowledgeChunkSearchService.class);
        when(search.indexVersionChunks(any(), any())).thenReturn(Mono.empty());
        versionEmbeddings = mock(org.example.ai_agent.service.EmbeddingService.class);
        embeddingBatches = mock(DocumentEmbeddingBatchProcessor.class);
        processor = new VersionedDocumentProcessor(fence, leases, databaseClient, versionRepository,
                repositories.getRepository(org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository.class),
                repositories.getRepository(org.example.ai_agent.repository.knowledge.KnowledgeDocumentChunkRepository.class),
                stages, parser, versionEmbeddings,
                embeddingBatches, search, recordRepository, realOutbox, objectMapper);
        cleanup = new org.example.ai_agent.service.impl.knowledge.KnowledgeCleanupCommands(databaseClient, transaction);
        cleanupEmbeddings = mock(org.example.ai_agent.service.EmbeddingService.class);
        when(cleanupEmbeddings.deleteDocumentEmbeddings(any())).thenReturn(Mono.just(true));
        when(cleanupEmbeddings.deleteKnowledgeBaseEmbeddings(any())).thenReturn(Mono.just(true));
        when(search.deleteDocumentChunks(any())).thenReturn(Mono.empty());
        when(search.deleteKnowledgeBaseChunks(any())).thenReturn(Mono.empty());
        cleanupOss = mock(org.example.ai_agent.service.OssService.class);
        when(cleanupOss.deleteFile(any())).thenReturn(Mono.just(true));
        cleanupWorker = new org.example.ai_agent.service.impl.knowledge.KnowledgeCleanupWorker(databaseClient,
                transaction, cleanup, cleanupEmbeddings, search, cleanupOss, new SimpleMeterRegistry());

        DocumentProcessingOutboxService failingOutbox = mock(DocumentProcessingOutboxService.class);
        when(failingOutbox.enqueue(any(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("outbox unavailable")));
        failingOutboxService = new IngestionRunCommandService(
                databaseClient, versionRepository, runRepository, recordRepository,
                failingOutbox, transaction, objectMapper);
    }

    @BeforeEach
    void resetDocument() {
        when(stages.parseDocumentFromOss(any(), anyString())).thenReturn(Mono.just("version content"));
        when(parser.splitDocument(any())).thenReturn(Mono.just(List.of(
                dev.langchain4j.data.segment.TextSegment.from("version content"))));
        when(cleanupEmbeddings.deleteDocumentEmbeddings(any())).thenReturn(Mono.just(true));
        when(search.indexVersionChunks(any(), any())).thenReturn(Mono.empty());
        when(search.deleteDocumentChunks(any())).thenReturn(Mono.empty());
        when(cleanupOss.deleteFile(any())).thenReturn(Mono.just(true));
        databaseClient.sql("DELETE FROM asset_cleanup_tasks").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM document_processing_outbox").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM document_processing_records").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM ingestion_runs").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM document_versions").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM knowledge_documents").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM knowledge_bases").fetch().rowsUpdated())
                .then(databaseClient.sql("""
                        INSERT INTO knowledge_bases (id, name, creator_id, status, document_count)
                        VALUES (:knowledgeBaseId, 'command integration', 9320001, 'ACTIVE', 1)
                        """)
                        .bind("knowledgeBaseId", KNOWLEDGE_BASE_ID)
                        .fetch().rowsUpdated())
                .then(databaseClient.sql("""
                        INSERT INTO knowledge_documents (
                            id, knowledge_base_id, title, file_name, file_type, file_size,
                            oss_path, oss_url, uploader_id
                        ) VALUES (
                            :documentId, :knowledgeBaseId, 'command document', 'command.txt', 'txt', 7,
                            'command/path', 'https://example.invalid/command', 9320001
                        )
                        """)
                        .bind("documentId", DOCUMENT_ID)
                        .bind("knowledgeBaseId", KNOWLEDGE_BASE_ID)
                        .fetch().rowsUpdated())
                .then()
                .block();
    }

    @Test
    void legacyChunkVisibilityMatchesProjectionAndStopsAfterVersionedAcceptance() {
        databaseClient.sql("UPDATE knowledge_documents SET parse_status = 'COMPLETED' WHERE id = :id")
                .bind("id", DOCUMENT_ID).fetch().rowsUpdated().block();
        databaseClient.sql("""
                INSERT INTO knowledge_document_chunks
                    (document_id, knowledge_base_id, generation, chunk_index, content, embedding_status)
                VALUES (:doc, :kb, 1, 0, 'legacy evidence', 'COMPLETED')
                """).bind("doc", DOCUMENT_ID).bind("kb", KNOWLEDGE_BASE_ID).fetch().rowsUpdated().block();
        var visibility = new org.example.ai_agent.service.impl.knowledge.ActiveKnowledgeChunks(databaseClient);
        assertEquals(1L, chunks.countByDocumentId(DOCUMENT_ID).block());
        assertEquals(1L, chunks.findIndexRowsByDocumentId(DOCUMENT_ID).count().block());
        assertEquals(1, visibility.ids(List.of(KNOWLEDGE_BASE_ID)).block().size());

        databaseClient.sql("INSERT INTO document_versions (document_id, generation, activation_status) VALUES (:id, 1, 'ACTIVE')")
                .bind("id", DOCUMENT_ID).fetch().rowsUpdated().block();
        databaseClient.sql("""
                UPDATE knowledge_documents d JOIN document_versions v ON v.document_id = d.id AND v.generation = 1
                SET d.active_version_id = v.id WHERE d.id = :id
                """).bind("id", DOCUMENT_ID).fetch().rowsUpdated().block();
        assertEquals(1L, chunks.countByDocumentId(DOCUMENT_ID).block());
        assertEquals(1L, chunks.findIndexRowsByDocumentId(DOCUMENT_ID).count().block());
        assertEquals(1, visibility.ids(List.of(KNOWLEDGE_BASE_ID)).block().size());

        var accepted = service.acceptInitialIngestion(DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", false).block();
        // A stale legacy write must not become visible after the document enters versioned processing.
        databaseClient.sql("UPDATE knowledge_document_chunks SET version_id = NULL WHERE document_id = :id")
                .bind("id", DOCUMENT_ID).fetch().rowsUpdated().block();
        assertTrue(accepted.newlyCreated());
        assertEquals(0L, chunks.countByDocumentId(DOCUMENT_ID).block());
        assertEquals(0L, chunks.findIndexRowsByDocumentId(DOCUMENT_ID).count().block());
        assertTrue(visibility.ids(List.of(KNOWLEDGE_BASE_ID)).block().isEmpty());
    }

    @Test
    void acceptanceCommitsVersionRunRecordAndOutboxAsOneFact() {
        StepVerifier.create(service.acceptInitialIngestion(
                        DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", true))
                .assertNext(accepted -> {
                    assertTrue(accepted.newlyCreated());
                    assertEquals(1L, accepted.generation());
                    assertEquals(DOCUMENT_ID, accepted.documentId());
                })
                .verifyComplete();

        assertEquals(1L, count("document_versions").block());
        assertEquals(1L, count("ingestion_runs").block());
        assertEquals(1L, count("document_processing_records").block());
        assertEquals(1L, count("document_processing_outbox").block());
        assertEquals(1L, scalarLong("""
                SELECT COUNT(*)
                FROM ingestion_runs run_row
                INNER JOIN document_versions version_row ON version_row.id = run_row.version_id
                INNER JOIN document_processing_records record_row ON record_row.run_id = run_row.id
                INNER JOIN document_processing_outbox outbox_row ON outbox_row.run_id = run_row.id
                WHERE run_row.document_id = :documentId
                  AND run_row.generation = 1
                  AND run_row.current_stage = 'PARSE'
                  AND run_row.status = 'PENDING'
                  AND record_row.generation = 1
                  AND outbox_row.version_id = version_row.id
                  AND outbox_row.generation = 1
                """).block());
    }

    @Test
    void concurrentAcceptanceReturnsOneNewRunAndOneReplay() {
        Mono<List<AcceptedIngestionRun>> accepted = Flux.merge(
                        service.acceptInitialIngestion(
                                        DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", true)
                                .subscribeOn(Schedulers.parallel()),
                        service.acceptInitialIngestion(
                                        DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", true)
                                .subscribeOn(Schedulers.parallel()))
                .collectList();

        StepVerifier.create(accepted)
                .assertNext(results -> {
                    assertEquals(2, results.size());
                    assertEquals(1, results.stream().filter(AcceptedIngestionRun::newlyCreated).count());
                    assertEquals(results.getFirst().runId(), results.getLast().runId());
                    assertEquals(results.getFirst().versionId(), results.getLast().versionId());
                })
                .verifyComplete();

        assertEquals(1L, count("document_versions").block());
        assertEquals(1L, count("ingestion_runs").block());
        assertEquals(1L, count("document_processing_records").block());
        assertEquals(1L, count("document_processing_outbox").block());
    }

    @Test
    void outboxFailureRollsBackVersionRunAndStageRecord() {
        StepVerifier.create(failingOutboxService.acceptInitialIngestion(
                        DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", true))
                .expectErrorMatches(error -> error.getMessage().contains("outbox unavailable"))
                .verify();

        assertEquals(0L, count("document_versions").block());
        assertEquals(0L, count("ingestion_runs").block());
        assertEquals(0L, count("document_processing_records").block());
        assertEquals(0L, count("document_processing_outbox").block());
    }

    @Test
    void differentActionConflictsWithoutAllocatingAnotherVersion() {
        service.acceptInitialIngestion(DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", true).block();
        StepVerifier.create(service.accept(DOCUMENT_ID, KNOWLEDGE_BASE_ID,
                        org.example.ai_agent.knowledge.ingestion.IngestionAction.REPARSE, "auto", true))
                .expectErrorMatches(error -> error.getMessage().contains("INGESTION_ACTION_CONFLICT")).verify();
        assertEquals(1L, count("document_versions").block());
    }

    @Test
    void deletionRejectsAcceptanceWithoutAnyNewResources() {
        databaseClient.sql("UPDATE knowledge_bases SET deletion_status = 'DELETING'").fetch().rowsUpdated().block();
        StepVerifier.create(service.acceptInitialIngestion(DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", true))
                .expectError(IllegalArgumentException.class).verify();
        assertEquals(0L, count("ingestion_runs").block());
    }

    @Test
    void reembedWithoutActiveParsedVersionRollsBackAllocation() {
        StepVerifier.create(service.accept(DOCUMENT_ID, KNOWLEDGE_BASE_ID,
                        org.example.ai_agent.knowledge.ingestion.IngestionAction.REEMBED, "auto", true))
                .expectErrorMatches(error -> error.getMessage().contains("REEMBED_REQUIRES_ACTIVE_PARSED_VERSION"))
                .verify();
        assertEquals(0L, count("document_versions").block());
        assertEquals(0L, count("ingestion_runs").block());
    }

    @Test
    void expiredLeaseNeverInvokesStageWrites() {
        var accepted = service.acceptInitialIngestion(DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", true).block();
        var message = org.example.ai_agent.dto.document.DocumentProcessingMessage.createParseMessage(
                DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", true);
        message.setRunId(accepted.runId()); message.setVersionId(accepted.versionId()); message.setGeneration(accepted.generation());
        var claim = leases.claim(message).block();
        databaseClient.sql("UPDATE ingestion_runs SET lease_until = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 SECOND)")
                .fetch().rowsUpdated().block();
        var invoked = new java.util.concurrent.atomic.AtomicBoolean();
        StepVerifier.create(fence.execute(claim, message, () -> { invoked.set(true); return Mono.just(true); }))
                .expectError(IngestionLeaseLostException.class).verify();
        org.junit.jupiter.api.Assertions.assertFalse(invoked.get());
    }

    @Test
    void stageWriteFailureRollsBackEarlierWrites() {
        var accepted = service.acceptInitialIngestion(DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", true).block();
        var message = org.example.ai_agent.dto.document.DocumentProcessingMessage.createParseMessage(
                DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", true);
        message.setRunId(accepted.runId()); message.setVersionId(accepted.versionId()); message.setGeneration(accepted.generation());
        var claim = leases.claim(message).block();
        StepVerifier.create(fence.execute(claim, message, () -> databaseClient.sql(
                        "UPDATE document_versions SET content = 'must rollback'").fetch().rowsUpdated()
                        .then(Mono.error(new IllegalStateException("simulated outbox failure")))))
                .expectError(IllegalStateException.class).verify();
        assertEquals(0L, databaseClient.sql("SELECT COUNT(*) FROM document_versions WHERE content IS NOT NULL")
                .map((row, meta) -> row.get(0, Long.class)).one().block());
    }

    @Test
    void committedVectorsSurviveProjectionFailureWithoutCallingEmbeddingAgain() {
        var first = service.acceptInitialIngestion(DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", false).block();
        runStage(first, "PARSE"); runStage(first, "CHUNK"); runStage(first, "EMBED");
        var next = service.accept(DOCUMENT_ID, KNOWLEDGE_BASE_ID,
                org.example.ai_agent.knowledge.ingestion.IngestionAction.REEMBED, "auto", true).block();
        when(embeddingBatches.resolveEmbeddingProfile(KNOWLEDGE_BASE_ID)).thenReturn(Mono.just(
                new org.example.ai_agent.service.registry.EmbeddingStoreRegistry.EmbeddingProfile(
                        "local-model-stub", "v1", 2, "test-vectors", true)));
        when(embeddingBatches.refreshEmbeddingIndex(KNOWLEDGE_BASE_ID)).thenReturn(Mono.empty());
        when(versionEmbeddings.embedTexts(any(), anyString())).thenReturn(Mono.just(List.of(
                dev.langchain4j.data.embedding.Embedding.from(new float[]{1, 0}))));
        when(versionEmbeddings.storeEmbeddings(any(), any(), any(), any(), any(), anyString(), anyString()))
                .thenAnswer(invocation -> Mono.just(invocation.<List<Long>>getArgument(2).stream()
                        .map(id -> "chunk-" + id).toList()));
        when(search.indexVersionChunks(DOCUMENT_ID, next.versionId()))
                .thenReturn(Mono.error(new IllegalStateException("projection unavailable")));
        var message = stageMessage(next, "EMBED");
        message.setEnableVectorization(true);
        var claim = leases.claim(message).block();
        StepVerifier.create(processor.process(claim, message)).expectError(IllegalStateException.class).verify();
        assertEquals("COMPLETED", chunks.findByVersionIdOrderByChunkIndex(next.versionId()).blockFirst().getEmbeddingStatus());
        assertEquals(first.versionId(), scalarLong("SELECT active_version_id FROM knowledge_documents WHERE id = :documentId").block());
        message.setRetryCount(1);
        processor.retry(claim, message, "document.embed.retry").block();
        when(search.indexVersionChunks(DOCUMENT_ID, next.versionId())).thenReturn(Mono.empty());
        runStage(next, "EMBED", true);
        assertEquals(next.versionId(), scalarLong("SELECT active_version_id FROM knowledge_documents WHERE id = :documentId").block());
        org.mockito.Mockito.verify(versionEmbeddings, org.mockito.Mockito.times(1)).embedTexts(any(), anyString());
        org.mockito.Mockito.verify(versionEmbeddings, org.mockito.Mockito.times(1))
                .storeEmbeddings(any(), any(), any(), any(), any(), anyString(), anyString());
    }

    @Test
    void searchFailureKeepsOldVersionAndRetryActivatesWithoutParsingAgain() {
        var first = service.acceptInitialIngestion(DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", false).block();
        runStage(first, "PARSE"); runStage(first, "CHUNK"); runStage(first, "EMBED");
        var next = service.accept(DOCUMENT_ID, KNOWLEDGE_BASE_ID,
                org.example.ai_agent.knowledge.ingestion.IngestionAction.REPARSE, "auto", false).block();
        runStage(next, "PARSE"); runStage(next, "CHUNK");
        var message = stageMessage(next, "EMBED");
        var claim = leases.claim(message).block();
        when(search.indexVersionChunks(DOCUMENT_ID, next.versionId()))
                .thenReturn(Mono.error(new IllegalStateException("projection unavailable")));
        StepVerifier.create(processor.process(claim, message)).expectError(IllegalStateException.class).verify();
        assertEquals(first.versionId(), scalarLong("SELECT active_version_id FROM knowledge_documents WHERE id = :documentId").block());
        message.setRetryCount(1);
        processor.retry(claim, message, "document.embed.retry").block();
        when(search.indexVersionChunks(DOCUMENT_ID, next.versionId())).thenReturn(Mono.empty());
        runStage(next, "EMBED");
        assertEquals(next.versionId(), scalarLong("SELECT active_version_id FROM knowledge_documents WHERE id = :documentId").block());
        assertEquals(1L, databaseClient.sql("SELECT COUNT(*) FROM asset_cleanup_tasks WHERE resource_type = 'VERSION'")
                .map((row, meta) -> row.get(0, Long.class)).one().block());
        assertEquals(2L, scalarLong("SELECT COUNT(*) FROM knowledge_document_chunks WHERE document_id = :documentId").block());
    }

    @Test
    void reembedCopiesChunksWithFreshIdsAndPreservesOldContent() {
        var first = service.acceptInitialIngestion(DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", false).block();
        runStage(first, "PARSE"); runStage(first, "CHUNK"); runStage(first, "EMBED");
        var next = service.accept(DOCUMENT_ID, KNOWLEDGE_BASE_ID,
                org.example.ai_agent.knowledge.ingestion.IngestionAction.REEMBED, "auto", true).block();
        assertEquals(org.example.ai_agent.knowledge.ingestion.IngestionStage.EMBED, next.firstStage());
        assertEquals(2L, scalarLong("SELECT COUNT(*) FROM knowledge_document_chunks WHERE document_id = :documentId").block());
        assertEquals(first.versionId(), scalarLong("SELECT active_version_id FROM knowledge_documents WHERE id = :documentId").block());
        assertEquals(2L, databaseClient.sql("SELECT COUNT(*) FROM document_versions WHERE content = 'version content'")
                .map((row, meta) -> row.get(0, Long.class)).one().block());
    }

    @Test
    void emptyDocumentCompletesWithoutClaimingVectorization() {
        when(stages.parseDocumentFromOss(any(), anyString())).thenReturn(Mono.just(""));
        when(parser.splitDocument(any())).thenReturn(Mono.just(List.of()));
        var accepted = service.acceptInitialIngestion(DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", true).block();
        runStage(accepted, "PARSE", true);
        runStage(accepted, "CHUNK", true);
        runStage(accepted, "EMBED", true);

        assertEquals("SKIPPED", databaseClient.sql("SELECT embedding_status FROM document_versions WHERE id = :id")
                .bind("id", accepted.versionId()).map((row, meta) -> row.get(0, String.class)).one().block());
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM knowledge_documents WHERE id = :documentId AND is_embedded = FALSE").block());
        assertEquals(0L, scalarLong("SELECT chunk_count FROM knowledge_documents WHERE id = :documentId").block());
    }

    private void runStage(AcceptedIngestionRun accepted, String stage) {
        runStage(accepted, stage, false);
    }

    private void runStage(AcceptedIngestionRun accepted, String stage, boolean vectorization) {
        var message = stageMessage(accepted, stage);
        message.setEnableVectorization(vectorization);
        assertTrue(processor.process(leases.claim(message).block(), message).block());
    }

    private org.example.ai_agent.dto.document.DocumentProcessingMessage stageMessage(AcceptedIngestionRun accepted, String stage) {
        var message = org.example.ai_agent.dto.document.DocumentProcessingMessage.createParseMessage(
                DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", false);
        message.setRunId(accepted.runId()); message.setVersionId(accepted.versionId());
        message.setGeneration(accepted.generation()); message.setProcessingType(stage);
        return message;
    }

    @Test
    void durableDeleteIsIdempotentAndCancelsActiveRunBeforeCleanup() {
        var accepted = service.acceptInitialIngestion(DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", false).block();
        var operation = cleanup.deleteDocument(DOCUMENT_ID, KNOWLEDGE_BASE_ID, false).block();
        assertEquals(operation.operationId(), cleanup.deleteDocument(DOCUMENT_ID, KNOWLEDGE_BASE_ID, false).block().operationId());
        assertEquals(0L, databaseClient.sql("SELECT document_count FROM knowledge_bases WHERE id = :id")
                .bind("id", KNOWLEDGE_BASE_ID).map((row, meta) -> row.get(0, Long.class)).one().block());
        assertEquals("CANCELLED", databaseClient.sql("SELECT status FROM ingestion_runs WHERE id = :id")
                .bind("id", accepted.runId()).map((row, meta) -> row.get(0, String.class)).one().block());
        StepVerifier.create(service.acceptInitialIngestion(DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", false))
                .expectError(IllegalArgumentException.class).verify();
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM knowledge_documents WHERE id = :documentId").block());
        makeCleanupDue();
        for (int i = 0; i < 5; i++) cleanupWorker.drain().block(java.time.Duration.ofSeconds(30));
        assertEquals("COMPLETED", cleanup.get(operation.operationId(), KNOWLEDGE_BASE_ID, false).block().status());
        assertEquals(0L, scalarLong("SELECT COUNT(*) FROM knowledge_documents WHERE id = :documentId").block());
    }

    @Test
    void externalCleanupFailureKeepsMainRecordHiddenAndRetriesSameStep() {
        var operation = cleanup.deleteDocument(DOCUMENT_ID, KNOWLEDGE_BASE_ID, false).block();
        when(cleanupEmbeddings.deleteDocumentEmbeddings(DOCUMENT_ID)).thenReturn(Mono.just(false));
        makeCleanupDue(); cleanupWorker.drain().block(java.time.Duration.ofSeconds(30));
        var failed = cleanup.get(operation.operationId(), KNOWLEDGE_BASE_ID, false).block();
        assertEquals("PENDING", failed.status()); assertEquals(0, failed.step()); assertEquals(1, failed.attempt());
        assertEquals("CLEANUP_STEP_FAILED", failed.errorCode());
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM knowledge_documents WHERE id = :documentId AND deletion_status = 'DELETING'").block());
        when(cleanupEmbeddings.deleteDocumentEmbeddings(DOCUMENT_ID)).thenReturn(Mono.just(true));
        makeCleanupDue();
        for (int i = 0; i < 5; i++) cleanupWorker.drain().block(java.time.Duration.ofSeconds(30));
        assertEquals("COMPLETED", cleanup.get(operation.operationId(), KNOWLEDGE_BASE_ID, false).block().status());
    }

    @Test
    void searchAndOssCleanupFailuresRetryWithoutAdvancingTheirSteps() {
        var operation = cleanup.deleteDocument(DOCUMENT_ID, KNOWLEDGE_BASE_ID, false).block();
        makeCleanupDue(); cleanupWorker.drain().block(java.time.Duration.ofSeconds(30));

        when(search.deleteDocumentChunks(DOCUMENT_ID)).thenReturn(Mono.error(new IllegalStateException("search unavailable")));
        makeCleanupDue(); cleanupWorker.drain().block(java.time.Duration.ofSeconds(30));
        var searchFailure = cleanup.get(operation.operationId(), KNOWLEDGE_BASE_ID, false).block();
        assertEquals("PENDING", searchFailure.status()); assertEquals(1, searchFailure.step());
        assertEquals("CLEANUP_STEP_FAILED", searchFailure.errorCode());

        when(search.deleteDocumentChunks(DOCUMENT_ID)).thenReturn(Mono.empty());
        makeCleanupDue(); cleanupWorker.drain().block(java.time.Duration.ofSeconds(30));
        when(cleanupOss.deleteFile("command/path")).thenReturn(Mono.just(false));
        makeCleanupDue(); cleanupWorker.drain().block(java.time.Duration.ofSeconds(30));
        var ossFailure = cleanup.get(operation.operationId(), KNOWLEDGE_BASE_ID, false).block();
        assertEquals("PENDING", ossFailure.status()); assertEquals(2, ossFailure.step());
        assertEquals("CLEANUP_STEP_FAILED", ossFailure.errorCode());

        when(cleanupOss.deleteFile("command/path")).thenReturn(Mono.just(true));
        for (int i = 0; i < 3; i++) { makeCleanupDue(); cleanupWorker.drain().block(java.time.Duration.ofSeconds(30)); }
        assertEquals("COMPLETED", cleanup.get(operation.operationId(), KNOWLEDGE_BASE_ID, false).block().status());
    }

    @Test
    void knowledgeBaseDeleteDispatchesChildCleanupAndRejectsUploadAcceptance() {
        var operation = cleanup.deleteKnowledgeBase(KNOWLEDGE_BASE_ID, KNOWLEDGE_BASE_ID, false).block();
        StepVerifier.create(service.acceptInitialIngestion(DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", false))
                .expectError(IllegalArgumentException.class).verify();
        for (int i = 0; i < 8; i++) { makeCleanupDue(); cleanupWorker.drain().block(java.time.Duration.ofSeconds(30)); }
        assertEquals("COMPLETED", cleanup.get(operation.operationId(), KNOWLEDGE_BASE_ID, false).block().status());
        assertEquals(0L, databaseClient.sql("SELECT COUNT(*) FROM knowledge_bases WHERE id = :id")
                .bind("id", KNOWLEDGE_BASE_ID).map((row, meta) -> row.get(0, Long.class)).one().block());
    }

    private void makeCleanupDue() {
        databaseClient.sql("UPDATE asset_cleanup_tasks SET next_attempt_at = CURRENT_TIMESTAMP WHERE status = 'PENDING'")
                .fetch().rowsUpdated().block();
    }

    private Mono<Long> count(String table) {
        if (!List.of(
                "document_versions", "ingestion_runs",
                "document_processing_records", "document_processing_outbox").contains(table)) {
            return Mono.error(new IllegalArgumentException("Unsupported table"));
        }
        return databaseClient.sql("SELECT COUNT(*) FROM " + table)
                .map((row, metadata) -> row.get(0, Long.class))
                .one();
    }

    private Mono<Long> scalarLong(String sql) {
        return databaseClient.sql(sql)
                .bind("documentId", DOCUMENT_ID)
                .map((row, metadata) -> row.get(0, Long.class))
                .one();
    }

    private record MySqlTestSettings(String host, int port, String database, String username, String password) {

        static MySqlTestSettings fromEnvironment() {
            return new MySqlTestSettings(
                    required("AI_CHAT_MYSQL_IT_HOST"),
                    Integer.parseInt(optional("AI_CHAT_MYSQL_IT_PORT", "3306")),
                    required("AI_CHAT_MYSQL_IT_DATABASE"),
                    required("AI_CHAT_MYSQL_IT_USERNAME"),
                    required("AI_CHAT_MYSQL_IT_PASSWORD"));
        }

        void requireIsolatedDatabase() {
            if (!database.matches("ai_chat_it_[A-Za-z0-9_]+")) {
                throw new IllegalStateException("Integration database must be isolated");
            }
        }

        String jdbcUrl() {
            return "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Missing environment variable: " + name);
            }
            return value;
        }

        private static String optional(String name, String defaultValue) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? defaultValue : value;
        }
    }
}
