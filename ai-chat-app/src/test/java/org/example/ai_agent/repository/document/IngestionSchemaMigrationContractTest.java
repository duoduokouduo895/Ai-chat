package org.example.ai_agent.repository.document;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestionSchemaMigrationContractTest {

    @Test
    void v8MustExpandVersionedIngestionWithoutRelaxingLegacyChunkUniqueness() throws IOException {
        String migration = migrationSql();

        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS document_versions"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS ingestion_runs"));
        assertTrue(migration.contains("ADD COLUMN active_version_id BIGINT NULL"));
        assertTrue(migration.contains("ADD COLUMN lease_token CHAR(36) NULL"));
        assertTrue(migration.contains("ADD COLUMN lease_until DATETIME NULL"));
        assertTrue(migration.contains("SET generation = 1"));
        assertTrue(migration.contains("uk_records_run_stage"));
        assertTrue(migration.contains("idx_chunks_document_generation_index"));
        assertFalse(migration.contains("DROP INDEX uk_chunks_document_index"),
                "V8 must keep legacy chunk uniqueness until every writer supplies generation");
    }

    private String migrationSql() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V8__add_document_versions_and_ingestion_runs.sql")) {
            if (input == null) {
                throw new IllegalStateException("V8 migration resource is missing");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
