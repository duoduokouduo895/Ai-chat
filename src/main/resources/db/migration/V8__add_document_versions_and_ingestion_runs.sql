-- Expand-only persistence for versioned document ingestion.
--
-- This migration deliberately keeps uk_chunks_document_index in place. The
-- generation-aware unique key is introduced only after every writer supplies
-- a non-null generation; otherwise legacy writers could bypass uniqueness via
-- SQL NULL semantics. Existing documents and related rows are backfilled as
-- generation 1 without deleting or rewriting their current business fields.

CREATE TABLE IF NOT EXISTS document_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    generation BIGINT NOT NULL,
    parse_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    chunk_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    activation_status VARCHAR(20) NOT NULL DEFAULT 'BUILDING',
    content LONGTEXT NULL,
    character_count INT NULL DEFAULT 0,
    chunk_count INT NULL DEFAULT 0,
    metadata JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    activated_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_versions_document_generation (document_id, generation),
    KEY idx_document_versions_document_activation (document_id, activation_status, generation DESC),
    CONSTRAINT fk_document_versions_document
        FOREIGN KEY (document_id) REFERENCES knowledge_documents (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ingestion_runs (
    id CHAR(36) NOT NULL,
    document_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    generation BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    current_stage VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt INT NOT NULL DEFAULT 0,
    lease_token CHAR(36) NULL,
    lease_until DATETIME NULL,
    error_code VARCHAR(100) NULL,
    error_message TEXT NULL,
    started_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ingestion_runs_version (version_id),
    UNIQUE KEY uk_ingestion_runs_document_generation (document_id, generation),
    KEY idx_ingestion_runs_status_lease (status, lease_until, updated_at),
    KEY idx_ingestion_runs_document_created (document_id, created_at DESC),
    CONSTRAINT fk_ingestion_runs_document
        FOREIGN KEY (document_id) REFERENCES knowledge_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_ingestion_runs_knowledge_base
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_bases (id) ON DELETE CASCADE,
    CONSTRAINT fk_ingestion_runs_version
        FOREIGN KEY (version_id) REFERENCES document_versions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_documents'
          AND column_name = 'active_version_id'
    ),
    'SELECT 1',
    'ALTER TABLE knowledge_documents ADD COLUMN active_version_id BIGINT NULL AFTER id'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_documents'
          AND index_name = 'idx_documents_active_version'
    ),
    'SELECT 1',
    'CREATE INDEX idx_documents_active_version ON knowledge_documents (active_version_id)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_document_chunks'
          AND column_name = 'version_id'
    ),
    'SELECT 1',
    'ALTER TABLE knowledge_document_chunks ADD COLUMN version_id BIGINT NULL AFTER document_id'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_document_chunks'
          AND column_name = 'generation'
    ),
    'SELECT 1',
    'ALTER TABLE knowledge_document_chunks ADD COLUMN generation BIGINT NULL DEFAULT 1 AFTER version_id'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_document_chunks'
          AND index_name = 'idx_chunks_document_generation_index'
    ),
    'SELECT 1',
    'CREATE INDEX idx_chunks_document_generation_index ON knowledge_document_chunks (document_id, generation, chunk_index)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'document_processing_records'
          AND column_name = 'run_id'
    ),
    'SELECT 1',
    'ALTER TABLE document_processing_records ADD COLUMN run_id CHAR(36) NULL AFTER id'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'document_processing_records'
          AND column_name = 'generation'
    ),
    'SELECT 1',
    'ALTER TABLE document_processing_records ADD COLUMN generation BIGINT NULL AFTER knowledge_base_id'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'document_processing_records'
          AND column_name = 'lease_token'
    ),
    'SELECT 1',
    'ALTER TABLE document_processing_records ADD COLUMN lease_token CHAR(36) NULL AFTER retry_count'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'document_processing_records'
          AND column_name = 'lease_until'
    ),
    'SELECT 1',
    'ALTER TABLE document_processing_records ADD COLUMN lease_until DATETIME NULL AFTER lease_token'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'document_processing_records'
          AND index_name = 'uk_records_run_stage'
    ),
    'SELECT 1',
    'CREATE UNIQUE INDEX uk_records_run_stage ON document_processing_records (run_id, processing_type)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'document_processing_records'
          AND index_name = 'idx_records_run_stage_lease'
    ),
    'SELECT 1',
    'CREATE INDEX idx_records_run_stage_lease ON document_processing_records (run_id, processing_type, status, lease_until)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'document_processing_outbox'
          AND column_name = 'run_id'
    ),
    'SELECT 1',
    'ALTER TABLE document_processing_outbox ADD COLUMN run_id CHAR(36) NULL AFTER id'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'document_processing_outbox'
          AND column_name = 'version_id'
    ),
    'SELECT 1',
    'ALTER TABLE document_processing_outbox ADD COLUMN version_id BIGINT NULL AFTER knowledge_base_id'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'document_processing_outbox'
          AND column_name = 'generation'
    ),
    'SELECT 1',
    'ALTER TABLE document_processing_outbox ADD COLUMN generation BIGINT NULL AFTER version_id'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'document_processing_outbox'
          AND index_name = 'idx_document_outbox_run_stage'
    ),
    'SELECT 1',
    'CREATE INDEX idx_document_outbox_run_stage ON document_processing_outbox (run_id, processing_type, id)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

INSERT INTO document_versions (
    document_id,
    generation,
    parse_status,
    chunk_status,
    embedding_status,
    activation_status,
    content,
    character_count,
    chunk_count,
    metadata,
    created_at,
    updated_at,
    activated_at
)
SELECT
    document_row.id,
    1,
    document_row.parse_status,
    CASE
        WHEN COALESCE(document_row.chunk_count, 0) > 0 THEN 'COMPLETED'
        ELSE 'PENDING'
    END,
    document_row.embedding_status,
    'ACTIVE',
    document_row.content,
    document_row.character_count,
    document_row.chunk_count,
    document_row.metadata,
    document_row.created_at,
    document_row.updated_at,
    document_row.updated_at
FROM knowledge_documents document_row
WHERE NOT EXISTS (
    SELECT 1
    FROM document_versions version_row
    WHERE version_row.document_id = document_row.id
      AND version_row.generation = 1
);

UPDATE knowledge_documents document_row
INNER JOIN document_versions version_row
        ON version_row.document_id = document_row.id
       AND version_row.generation = 1
SET document_row.active_version_id = version_row.id
WHERE document_row.active_version_id IS NULL;

UPDATE knowledge_document_chunks chunk_row
INNER JOIN document_versions version_row
        ON version_row.document_id = chunk_row.document_id
       AND version_row.generation = 1
SET chunk_row.version_id = COALESCE(chunk_row.version_id, version_row.id),
    chunk_row.generation = COALESCE(chunk_row.generation, 1)
WHERE chunk_row.version_id IS NULL
   OR chunk_row.generation IS NULL;

UPDATE document_processing_records
SET generation = 1
WHERE generation IS NULL;

UPDATE document_processing_outbox outbox_row
INNER JOIN document_versions version_row
        ON version_row.document_id = outbox_row.document_id
       AND version_row.generation = 1
SET outbox_row.version_id = COALESCE(outbox_row.version_id, version_row.id),
    outbox_row.generation = COALESCE(outbox_row.generation, 1)
WHERE outbox_row.version_id IS NULL
   OR outbox_row.generation IS NULL;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'knowledge_document_chunks'
          AND constraint_name = 'fk_chunks_version'
          AND constraint_type = 'FOREIGN KEY'
    ),
    'SELECT 1',
    'ALTER TABLE knowledge_document_chunks ADD CONSTRAINT fk_chunks_version FOREIGN KEY (version_id) REFERENCES document_versions (id) ON DELETE CASCADE'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'document_processing_records'
          AND constraint_name = 'fk_processing_records_run'
          AND constraint_type = 'FOREIGN KEY'
    ),
    'SELECT 1',
    'ALTER TABLE document_processing_records ADD CONSTRAINT fk_processing_records_run FOREIGN KEY (run_id) REFERENCES ingestion_runs (id) ON DELETE SET NULL'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'document_processing_outbox'
          AND constraint_name = 'fk_document_outbox_run'
          AND constraint_type = 'FOREIGN KEY'
    ),
    'SELECT 1',
    'ALTER TABLE document_processing_outbox ADD CONSTRAINT fk_document_outbox_run FOREIGN KEY (run_id) REFERENCES ingestion_runs (id) ON DELETE SET NULL'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'document_processing_outbox'
          AND constraint_name = 'fk_document_outbox_version'
          AND constraint_type = 'FOREIGN KEY'
    ),
    'SELECT 1',
    'ALTER TABLE document_processing_outbox ADD CONSTRAINT fk_document_outbox_version FOREIGN KEY (version_id) REFERENCES document_versions (id) ON DELETE SET NULL'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;
