ALTER TABLE knowledge_documents ADD COLUMN deletion_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE knowledge_bases ADD COLUMN deletion_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
CREATE TABLE asset_cleanup_tasks (
    id CHAR(36) NOT NULL PRIMARY KEY,
    resource_type VARCHAR(20) NOT NULL,
    resource_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    step INT NOT NULL DEFAULT 0,
    attempt INT NOT NULL DEFAULT 0,
    lease_token CHAR(36) NULL,
    lease_until DATETIME NULL,
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_code VARCHAR(100) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME NULL,
    UNIQUE KEY uk_cleanup_resource (resource_type, resource_id),
    KEY idx_cleanup_due (status, next_attempt_at, lease_until)
);
CREATE TABLE conversation_active_turn (
    conversation_id BIGINT NOT NULL PRIMARY KEY,
    turn_id CHAR(36) NOT NULL,
    UNIQUE KEY uk_conversation_active_turn (turn_id)
);
ALTER TABLE chat_turn ADD COLUMN retrieval_json JSON NULL;
