package org.example.ai_agent.document.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "AI_CHAT_MYSQL_IT_ENABLED", matches = "true")
class IngestionGenerationGuardMySqlIntegrationTest {
    private Settings settings;

    @BeforeAll
    void migrateOnlyThroughV9() {
        settings = Settings.fromEnvironment();
        settings.requireIsolatedDatabase();
        flyway("9").migrate();
    }

    @BeforeEach
    void resetRejectedMigrationInTheIsolatedDatabase() throws Exception {
        settings.requireIsolatedDatabase();
        flyway("9").repair();
        try (var connection = DriverManager.getConnection(settings.jdbcUrl(), settings.username(), settings.password());
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM knowledge_bases WHERE id = 9400001");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULL_GENERATION", "LEGACY_TASK", "ACTIVE_RUN"})
    void v10StopsBeforeConstraintChangesWhenPrerequisitesAreUnsafe(String unsafeCase) throws Exception {
        try (var connection = DriverManager.getConnection(settings.jdbcUrl(), settings.username(), settings.password());
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO knowledge_bases (id, name, creator_id) VALUES (9400001, 'guard', 9400001)");
            statement.executeUpdate("""
                    INSERT INTO knowledge_documents (id, knowledge_base_id, title, file_name, file_type,
                        file_size, oss_path, oss_url, uploader_id)
                    VALUES (9400002, 9400001, 'guard', 'guard.txt', 'txt', 1, 'guard/path',
                        'https://example.invalid/guard', 9400001)
                    """);
            statement.executeUpdate("""
                    INSERT INTO knowledge_document_chunks (document_id, knowledge_base_id, chunk_index,
                        content, character_count, generation)
                    VALUES (9400002, 9400001, 0, 'unsafe', 6, NULL)
                    """);
            if (!"NULL_GENERATION".equals(unsafeCase)) {
                statement.executeUpdate("UPDATE knowledge_document_chunks SET generation = 1 WHERE document_id = 9400002");
            }
            if ("LEGACY_TASK".equals(unsafeCase)) {
                statement.executeUpdate("""
                        INSERT INTO document_processing_records (document_id, processing_type, status)
                        VALUES (9400002, 'PARSE', 'PROCESSING')
                        """);
            }
            if ("ACTIVE_RUN".equals(unsafeCase)) {
                statement.executeUpdate("INSERT INTO document_versions (id, document_id, generation) VALUES (9400003, 9400002, 1)");
                statement.executeUpdate("""
                        INSERT INTO ingestion_runs (id, document_id, knowledge_base_id, version_id,
                            generation, action, current_stage, status)
                        VALUES ('94000000-0000-0000-0000-000000000001', 9400002, 9400001, 9400003,
                            1, 'START_INGESTION', 'PARSE', 'PENDING')
                        """);
            }
        }

        assertThrows(RuntimeException.class, () -> flyway(null).migrate());
        try (var connection = DriverManager.getConnection(settings.jdbcUrl(), settings.username(), settings.password());
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'knowledge_document_chunks'
                       AND column_name = 'generation' AND is_nullable = 'YES'
                     """)) {
            result.next();
            assertEquals(1, result.getInt(1));
        }
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(settings.jdbcUrl(), settings.username(), settings.password())
                .locations("classpath:db/migration").encoding("UTF-8").outOfOrder(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private record Settings(String host, int port, String database, String username, String password) {
        static Settings fromEnvironment() {
            return new Settings(required("AI_CHAT_MYSQL_IT_HOST"),
                    Integer.parseInt(optional("AI_CHAT_MYSQL_IT_PORT", "3306")),
                    required("AI_CHAT_MYSQL_IT_DATABASE"), required("AI_CHAT_MYSQL_IT_USERNAME"),
                    required("AI_CHAT_MYSQL_IT_PASSWORD"));
        }

        void requireIsolatedDatabase() {
            if (!database.matches("ai_chat_it_[A-Za-z0-9_]+")) {
                throw new IllegalStateException("Integration test database must be isolated");
            }
        }

        String jdbcUrl() {
            return "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
            return value;
        }

        private static String optional(String name, String fallback) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
