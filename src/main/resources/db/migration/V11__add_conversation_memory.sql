-- Derived long-conversation memory. message/chat_turn remain the source of truth;
-- every row here can be rebuilt from them and is removed with its conversation.

-- One row per conversation: the current summary version, its coverage watermark
-- (assistant message id, monotonic within a conversation) and the compaction lease.
CREATE TABLE IF NOT EXISTS conversation_memory (
    conversation_id BIGINT NOT NULL PRIMARY KEY,
    version INT NOT NULL DEFAULT 0,
    covered_through_seq BIGINT NOT NULL DEFAULT 0,
    model_id VARCHAR(120) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    lease_token CHAR(36) NULL,
    lease_until DATETIME NULL,
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempt INT NOT NULL DEFAULT 0,
    error_code VARCHAR(100) NULL,
    archive_bytes BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_conversation_memory_due (status, next_attempt_at, lease_until),
    CONSTRAINT fk_conversation_memory_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Immutable rolling summaries; only the latest few versions are retained.
CREATE TABLE IF NOT EXISTS conversation_summary (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    version INT NOT NULL,
    covered_through_seq BIGINT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    token_estimate INT NOT NULL,
    model_id VARCHAR(120) NULL,
    prompt_version VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_conversation_summary_version (conversation_id, version),
    CONSTRAINT fk_conversation_summary_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Constraints and preferences stated by the user, each tied to its source turn.
CREATE TABLE IF NOT EXISTS conversation_constraint (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    content VARCHAR(500) NOT NULL,
    source_seq BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_version INT NOT NULL,
    superseded_version INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_conversation_constraint_active (conversation_id, status),
    CONSTRAINT fk_conversation_constraint_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Redundant copy of summarized turns for per-conversation full-text recall.
-- Total size is bounded by assistant.memory.archive-max-size (oldest rows evicted first).
CREATE TABLE IF NOT EXISTS conversation_archive_turn (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    seq BIGINT NOT NULL,
    turn_id VARCHAR(64) NOT NULL,
    user_text MEDIUMTEXT NOT NULL,
    assistant_text MEDIUMTEXT NOT NULL,
    content_bytes INT NOT NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_conversation_archive_turn_seq (conversation_id, seq),
    FULLTEXT KEY ft_conversation_archive_turn (user_text, assistant_text) WITH PARSER ngram,
    CONSTRAINT fk_conversation_archive_turn_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
