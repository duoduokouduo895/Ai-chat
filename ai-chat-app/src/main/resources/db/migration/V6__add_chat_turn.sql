-- Durable idempotency and lifecycle state for a streamed chat request.
-- This is an expand-only migration. Existing message deduplication remains active
-- until the ChatTurn read path has completed its compatibility observation period.

CREATE TABLE IF NOT EXISTS chat_turn (
    id CHAR(36) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    client_request_id VARCHAR(100) NOT NULL,
    model_id VARCHAR(120) NULL,
    status VARCHAR(20) NOT NULL,
    user_message_id BIGINT NULL,
    assistant_message_id BIGINT NULL,
    attempt INT NOT NULL DEFAULT 0,
    error_code VARCHAR(100) NULL,
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_turn_actor_conversation_request
        (actor_type, actor_id, conversation_id, client_request_id),
    KEY idx_chat_turn_status_updated (status, updated_at),
    KEY idx_chat_turn_conversation_started (conversation_id, started_at DESC),
    CONSTRAINT fk_chat_turn_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_turn_user_message
        FOREIGN KEY (user_message_id) REFERENCES message (id) ON DELETE SET NULL,
    CONSTRAINT fk_chat_turn_assistant_message
        FOREIGN KEY (assistant_message_id) REFERENCES message (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
