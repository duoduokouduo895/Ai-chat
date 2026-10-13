-- Drain old workers before applying this migration. Do not repair live tasks implicitly.
CREATE TEMPORARY TABLE ingestion_generation_migration_guard (
    allowed INT NOT NULL,
    CONSTRAINT legacy_tasks_must_be_drained_and_generations_present CHECK (allowed = 1)
);
INSERT INTO ingestion_generation_migration_guard (allowed)
SELECT CASE WHEN
    EXISTS (SELECT 1 FROM knowledge_document_chunks WHERE generation IS NULL OR generation <= 0)
    OR EXISTS (SELECT 1 FROM document_processing_records
               WHERE run_id IS NULL AND status IN ('PENDING', 'PROCESSING', 'RETRY'))
    OR EXISTS (SELECT 1 FROM ingestion_runs WHERE status IN ('PENDING', 'PROCESSING', 'RETRY'))
    THEN 0 ELSE 1 END;
DROP TEMPORARY TABLE ingestion_generation_migration_guard;

ALTER TABLE knowledge_document_chunks
    MODIFY COLUMN generation BIGINT NOT NULL DEFAULT 1;

-- V3 may have replaced the original unique key with a non-unique hot-path
-- index. Drop the legacy unique key only when it still exists; an unconditional
-- DROP makes V10 fail on those already-optimized schemas.
SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_document_chunks'
          AND index_name = 'uk_chunks_document_index'
          AND non_unique = 0
    ),
    'ALTER TABLE knowledge_document_chunks DROP INDEX uk_chunks_document_index',
    'SELECT 1'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

-- A failed attempt may have completed the DDL before Flyway recorded the
-- failure. Add the generation-scoped key only when it is not already present.
SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_document_chunks'
          AND index_name = 'uk_chunks_document_generation_index'
          AND non_unique = 0
    ),
    'SELECT 1',
    'ALTER TABLE knowledge_document_chunks ADD UNIQUE KEY uk_chunks_document_generation_index (document_id, generation, chunk_index)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;
