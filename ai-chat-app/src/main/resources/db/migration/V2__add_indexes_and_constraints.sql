-- V2: Backfill the first batch of indexes for existing databases.
-- Every operation is conditional because historical environments may have drifted.

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'search_history'
          AND index_name = 'idx_search_history_user_time'
    ),
    'SELECT 1',
    'CREATE INDEX idx_search_history_user_time ON search_history (user_id, `timestamp` DESC, id DESC)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'search_history'
          AND index_name = 'idx_search_history_user_query_time'
    ),
    'SELECT 1',
    'CREATE INDEX idx_search_history_user_query_time ON search_history (user_id, query(100), `timestamp` DESC)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'users'
          AND index_name = 'idx_users_username'
    ),
    'DROP INDEX idx_users_username ON users',
    'SELECT 1'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'users'
          AND index_name = 'idx_users_email'
    ),
    'DROP INDEX idx_users_email ON users',
    'SELECT 1'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_bases'
          AND index_name = 'idx_kb_creator_status_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_kb_creator_status_created ON knowledge_bases (creator_id, status, created_at DESC)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_bases'
          AND index_name = 'idx_kb_public_status_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_kb_public_status_created ON knowledge_bases (is_public, status, created_at DESC)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;
