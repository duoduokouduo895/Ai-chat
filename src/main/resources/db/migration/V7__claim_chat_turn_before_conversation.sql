-- Claim a client request before creating a new conversation.
-- Existing rows remain valid; the old conversation-scoped unique key is kept
-- during the compatibility period while the request-scoped key becomes the
-- authoritative idempotency constraint.

-- Create the request-scoped unique key before relaxing conversation_id. If
-- historical duplicate request IDs exist, this statement fails before any
-- schema relaxation is applied and requires an explicit data decision.
SET @flyway_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'chat_turn'
          AND index_name = 'uk_chat_turn_actor_request'
    ),
    'SELECT 1',
    'CREATE UNIQUE INDEX uk_chat_turn_actor_request ON chat_turn (actor_type, actor_id, client_request_id)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'chat_turn'
          AND column_name = 'request_fingerprint'
    ),
    'SELECT 1',
    'ALTER TABLE chat_turn ADD COLUMN request_fingerprint CHAR(64) NULL AFTER client_request_id'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'chat_turn'
          AND column_name = 'conversation_id'
          AND is_nullable = 'NO'
    ),
    'ALTER TABLE chat_turn MODIFY COLUMN conversation_id BIGINT NULL',
    'SELECT 1'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;
