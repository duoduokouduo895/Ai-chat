SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND column_name = 'deduplication_key'
    ),
    'SELECT 1',
    'ALTER TABLE message ADD COLUMN deduplication_key VARCHAR(160) NULL'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'uk_message_deduplication_key'
    ),
    'SELECT 1',
    'CREATE UNIQUE INDEX uk_message_deduplication_key ON message (deduplication_key)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;
