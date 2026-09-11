package org.example.ai_agent.service.impl.document;

import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.example.ai_agent.config.properties.DocumentProcessingProperties;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.knowledge.ingestion.IngestionStage;
import org.example.ai_agent.knowledge.ingestion.StageClaimDisposition;
import org.example.ai_agent.knowledge.ingestion.StageLeaseClaim;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "AI_CHAT_MYSQL_IT_ENABLED", matches = "true")
class IngestionRunLeaseMySqlIntegrationTest {

    private static final String RUN_ID = "00000000-0000-0000-0000-000000000311";
    private static final long KNOWLEDGE_BASE_ID = 9310001L;
    private static final long DOCUMENT_ID = 9310002L;
    private static final long VERSION_ID = 9310003L;

    private DatabaseClient databaseClient;
    private IngestionRunLeaseService service;

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
        IngestionRunRepository repository = new R2dbcRepositoryFactory(
                new R2dbcEntityTemplate(connectionFactory))
                .getRepository(IngestionRunRepository.class);
        DocumentProcessingProperties properties = new DocumentProcessingProperties();
        properties.setIngestionLeaseDuration(Duration.ofMinutes(1));
        service = new IngestionRunLeaseService(repository, properties);
    }

    @BeforeEach
    void resetRun() {
        databaseClient.sql("DELETE FROM ingestion_runs").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM document_versions").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM knowledge_documents").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM knowledge_bases").fetch().rowsUpdated())
                .then(databaseClient.sql("""
                        INSERT INTO knowledge_bases (id, name, creator_id, status, document_count)
                        VALUES (:knowledgeBaseId, 'lease integration', 9310001, 'ACTIVE', 1)
                        """)
                        .bind("knowledgeBaseId", KNOWLEDGE_BASE_ID)
                        .fetch().rowsUpdated())
                .then(databaseClient.sql("""
                        INSERT INTO knowledge_documents (
                            id, knowledge_base_id, title, file_name, file_type, file_size,
                            oss_path, oss_url, uploader_id
                        ) VALUES (
                            :documentId, :knowledgeBaseId, 'lease document', 'lease.txt', 'txt', 5,
                            'lease/path', 'https://example.invalid/lease', 9310001
                        )
                        """)
                        .bind("documentId", DOCUMENT_ID)
                        .bind("knowledgeBaseId", KNOWLEDGE_BASE_ID)
                        .fetch().rowsUpdated())
                .then(databaseClient.sql("""
                        INSERT INTO document_versions (
                            id, document_id, generation, activation_status
                        ) VALUES (:versionId, :documentId, 1, 'BUILDING')
                        """)
                        .bind("versionId", VERSION_ID)
                        .bind("documentId", DOCUMENT_ID)
                        .fetch().rowsUpdated())
                .then(databaseClient.sql("""
                        INSERT INTO ingestion_runs (
                            id, document_id, knowledge_base_id, version_id, generation,
                            action, current_stage, status, attempt
                        ) VALUES (
                            :runId, :documentId, :knowledgeBaseId, :versionId, 1,
                            'START_INGESTION', 'PARSE', 'PENDING', 0
                        )
                        """)
                        .bind("runId", RUN_ID)
                        .bind("documentId", DOCUMENT_ID)
                        .bind("knowledgeBaseId", KNOWLEDGE_BASE_ID)
                        .bind("versionId", VERSION_ID)
                        .fetch().rowsUpdated())
                .then()
                .block();
    }

    @Test
    void concurrentConsumersProduceOneOwnerAndOneLeaseHeldResult() {
        Mono<List<StageLeaseClaim>> claims = Flux.merge(
                        service.claim(message()).subscribeOn(Schedulers.parallel()),
                        service.claim(message()).subscribeOn(Schedulers.parallel()))
                .collectList();

        StepVerifier.create(claims)
                .assertNext(results -> {
                    assertEquals(2, results.size());
                    assertEquals(1, results.stream().filter(StageLeaseClaim::isOwned).count());
                    assertEquals(1, results.stream()
                            .filter(result -> result.disposition() == StageClaimDisposition.LEASE_HELD)
                            .count());
                })
                .verifyComplete();

        assertEquals(1L, scalarLong("SELECT attempt FROM ingestion_runs WHERE id = :runId").block());
    }

    @Test
    void expiredLeaseCannotCommitEvenBeforeAnotherWorkerTakesOver() {
        StageLeaseClaim expired = service.claim(message()).block();
        databaseClient.sql("UPDATE ingestion_runs SET lease_until = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE) WHERE id = :runId")
                .bind("runId", RUN_ID).fetch().rowsUpdated().block();
        StepVerifier.create(service.completeStage(expired, IngestionStage.CHUNK))
                .expectError(IngestionLeaseLostException.class).verify();
    }

    @Test
    void expiredLeaseCanBeReclaimedAndOldTokenCannotAdvanceTheRun() {
        StageLeaseClaim oldClaim = service.claim(message()).block();
        assertNotNull(oldClaim);
        databaseClient.sql("""
                        UPDATE ingestion_runs
                        SET lease_until = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE)
                        WHERE id = :runId
                        """)
                .bind("runId", RUN_ID)
                .fetch().rowsUpdated()
                .block();

        StageLeaseClaim newClaim = service.claim(message()).block();
        assertNotNull(newClaim);
        assertEquals(StageClaimDisposition.CLAIMED, newClaim.disposition());
        assertNotEquals(oldClaim.leaseToken(), newClaim.leaseToken());

        StepVerifier.create(service.completeStage(oldClaim, IngestionStage.CHUNK))
                .expectError(IngestionLeaseLostException.class)
                .verify();
        StepVerifier.create(service.completeStage(newClaim, IngestionStage.CHUNK))
                .verifyComplete();

        assertEquals("CHUNK", scalarText(
                "SELECT current_stage FROM ingestion_runs WHERE id = :runId").block());
        assertEquals("PENDING", scalarText(
                "SELECT status FROM ingestion_runs WHERE id = :runId").block());
        assertEquals(0L, scalarLong(
                "SELECT COUNT(*) FROM ingestion_runs WHERE id = :runId AND lease_token IS NOT NULL").block());
    }

    @Test
    void retryReleasesTheLeaseAndCompletedStageIsIdempotent() {
        StageLeaseClaim first = service.claim(message()).block();
        assertNotNull(first);
        StepVerifier.create(service.releaseForRetry(first, "PARSER_TIMEOUT", "parser timed out"))
                .verifyComplete();

        StageLeaseClaim retry = service.claim(message()).block();
        assertNotNull(retry);
        assertNotEquals(first.leaseToken(), retry.leaseToken());
        StepVerifier.create(service.completeStage(retry, IngestionStage.CHUNK)).verifyComplete();

        StepVerifier.create(service.claim(message()))
                .assertNext(claim -> assertEquals(
                        StageClaimDisposition.ALREADY_COMPLETED, claim.disposition()))
                .verifyComplete();
        assertEquals(2L, scalarLong("SELECT attempt FROM ingestion_runs WHERE id = :runId").block());
    }

    private DocumentProcessingMessage message() {
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(
                DOCUMENT_ID, KNOWLEDGE_BASE_ID, "auto", true);
        message.setRunId(RUN_ID);
        message.setVersionId(VERSION_ID);
        message.setGeneration(1L);
        return message;
    }

    private Mono<Long> scalarLong(String sql) {
        return databaseClient.sql(sql)
                .bind("runId", RUN_ID)
                .map((row, metadata) -> row.get(0, Long.class))
                .one();
    }

    private Mono<String> scalarText(String sql) {
        return databaseClient.sql(sql)
                .bind("runId", RUN_ID)
                .map((row, metadata) -> row.get(0, String.class))
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
