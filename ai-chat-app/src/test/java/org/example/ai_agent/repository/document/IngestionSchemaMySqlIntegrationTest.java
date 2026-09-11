package org.example.ai_agent.repository.document;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Real MySQL upgrade contract for the V6 to V10 ingestion schema expansion. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "AI_CHAT_MYSQL_IT_ENABLED", matches = "true")
class IngestionSchemaMySqlIntegrationTest {

    private MySqlTestSettings settings;

    @BeforeAll
    void migrateFromV6WithLegacyRows() {
        settings = MySqlTestSettings.fromEnvironment();
        settings.requireIsolatedDatabase();

        flyway("6").migrate();
        insertLegacyRows();
        flyway(null).migrate();
    }

    @Test
    @Order(1)
    void v8BackfillsOneActiveGenerationWithoutChangingLegacyFacts() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertEquals(1L, singleLong(statement, """
                    SELECT COUNT(*) FROM document_versions
                    WHERE document_id = 9200001
                      AND generation = 1
                      AND activation_status = 'ACTIVE'
                      AND parse_status = 'COMPLETED'
                      AND chunk_status = 'COMPLETED'
                      AND embedding_status = 'COMPLETED'
                      AND content = 'legacy content'
                    """));
            assertEquals(singleLong(statement, """
                            SELECT id FROM document_versions
                            WHERE document_id = 9200001 AND generation = 1
                            """),
                    singleLong(statement, "SELECT active_version_id FROM knowledge_documents WHERE id = 9200001"));
            assertEquals(1L, singleLong(statement, """
                    SELECT COUNT(*)
                    FROM knowledge_document_chunks chunk_row
                    INNER JOIN document_versions version_row ON version_row.id = chunk_row.version_id
                    WHERE chunk_row.document_id = 9200001
                      AND chunk_row.generation = 1
                      AND version_row.generation = 1
                    """));
            assertEquals(1L, singleLong(statement, """
                    SELECT COUNT(*) FROM document_processing_records
                    WHERE document_id = 9200001 AND generation = 1 AND run_id IS NULL
                    """));
            assertEquals(1L, singleLong(statement, """
                    SELECT COUNT(*) FROM document_processing_outbox
                    WHERE document_id = 9200001
                      AND generation = 1
                      AND version_id IS NOT NULL
                      AND run_id IS NULL
                    """));
        }
    }

    @Test
    @Order(2)
    void v10CreatesLeaseAndGenerationScopedChunkConstraints() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertEquals(2L, singleLong(statement, """
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name IN ('document_versions', 'ingestion_runs')
                    """));
            assertEquals(4L, singleLong(statement, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 'document_processing_records'
                      AND column_name IN ('run_id', 'generation', 'lease_token', 'lease_until')
                    """));
            assertEquals(1L, singleLong(statement, """
                    SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'document_processing_records'
                      AND index_name = 'uk_records_run_stage'
                      AND non_unique = 0
                    """));
            assertEquals(0L, singleLong(statement, """
                    SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'knowledge_document_chunks'
                      AND index_name = 'uk_chunks_document_index'
                      AND non_unique = 0
                    """));
            assertEquals(1L, singleLong(statement, """
                    SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'knowledge_document_chunks'
                      AND index_name = 'uk_chunks_document_generation_index'
                      AND non_unique = 0
                    """));
            assertEquals(3L, singleLong(statement, """
                    SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'knowledge_document_chunks'
                      AND index_name = 'uk_chunks_document_generation_index'
                      AND column_name IN ('document_id', 'generation', 'chunk_index')
                    """));
            assertEquals(1L, singleLong(statement, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 'knowledge_document_chunks'
                      AND column_name = 'generation'
                      AND is_nullable = 'NO'
                    """));
        }
    }

    @Test
    @Order(3)
    void legacyWriterCanStillInsertRowsWithoutVersionFields() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO knowledge_documents (
                        id, knowledge_base_id, title, file_name, file_type, file_size,
                        oss_path, oss_url, uploader_id
                    ) VALUES (
                        9200002, 9200001, 'legacy writer', 'legacy-2.txt', 'txt', 8,
                        'legacy/2', 'https://example.invalid/2', 9200001
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO knowledge_document_chunks (
                        document_id, knowledge_base_id, chunk_index, content, character_count
                    ) VALUES (9200002, 9200001, 0, 'legacy chunk 2', 14)
                    """);

            assertEquals(1L, singleLong(statement, """
                    SELECT COUNT(*) FROM knowledge_document_chunks
                    WHERE document_id = 9200002
                      AND generation = 1
                      AND version_id IS NULL
                    """));
        }
    }

    @Test
    @Order(4)
    void deletingLegacyDocumentCascadesVersionedRows() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM knowledge_documents WHERE id = 9200001");

            assertEquals(0L, singleLong(statement,
                    "SELECT COUNT(*) FROM document_versions WHERE document_id = 9200001"));
            assertEquals(0L, singleLong(statement,
                    "SELECT COUNT(*) FROM knowledge_document_chunks WHERE document_id = 9200001"));
            assertEquals(0L, singleLong(statement,
                    "SELECT COUNT(*) FROM document_processing_records WHERE document_id = 9200001"));
            assertEquals(0L, singleLong(statement,
                    "SELECT COUNT(*) FROM document_processing_outbox WHERE document_id = 9200001"));
        }
    }

    private Flyway flyway(String target) {
        org.flywaydb.core.api.configuration.FluentConfiguration configuration = Flyway.configure()
                .dataSource(settings.jdbcUrl(), settings.username(), settings.password())
                .locations("classpath:db/migration")
                .encoding("UTF-8")
                .outOfOrder(true);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private void insertLegacyRows() {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO knowledge_bases (
                        id, name, creator_id, status, document_count
                    ) VALUES (9200001, 'V8 migration probe', 9200001, 'ACTIVE', 1)
                    """);
            statement.executeUpdate("""
                    INSERT INTO knowledge_documents (
                        id, knowledge_base_id, title, file_name, file_type, file_size,
                        oss_path, oss_url, parse_status, content, character_count,
                        chunk_count, is_embedded, embedding_status, uploader_id
                    ) VALUES (
                        9200001, 9200001, 'legacy document', 'legacy.txt', 'txt', 14,
                        'legacy/1', 'https://example.invalid/1', 'COMPLETED', 'legacy content', 14,
                        1, 1, 'COMPLETED', 9200001
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO knowledge_document_chunks (
                        document_id, knowledge_base_id, chunk_index, content,
                        character_count, is_embedded, embedding_status
                    ) VALUES (9200001, 9200001, 0, 'legacy content', 14, 1, 'COMPLETED')
                    """);
            statement.executeUpdate("""
                    INSERT INTO document_processing_records (
                        document_id, knowledge_base_id, processing_type, status,
                        retry_count, processing_result
                    ) VALUES (9200001, 9200001, 'EMBED', 'COMPLETED', 0, 'legacy completed')
                    """);
            statement.executeUpdate("""
                    INSERT INTO document_processing_outbox (
                        event_key, document_id, knowledge_base_id, processing_type,
                        routing_key, payload, status
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000008', 9200001, 9200001,
                        'EMBED', 'document.embed',
                        '{"documentId":9200001,"knowledgeBaseId":9200001,"processingType":"EMBED"}',
                        'PUBLISHED'
                    )
                    """);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to create the V6 ingestion compatibility probe", error);
        }
    }

    private Connection connection() throws Exception {
        Connection connection = DriverManager.getConnection(settings.jdbcUrl(), settings.username(), settings.password());
        assertNotNull(connection);
        return connection;
    }

    private long singleLong(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                throw new IllegalStateException("Expected one aggregate row");
            }
            return resultSet.getLong(1);
        }
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
                throw new IllegalStateException("Integration test database must match ai_chat_it_[A-Za-z0-9_]+");
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
