-- Transactional outbox for document processing messages.
-- This migration is append-only: once applied, do not edit, rename, or delete it.

CREATE TABLE IF NOT EXISTS document_processing_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_key CHAR(36) NOT NULL,
    document_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    processing_type VARCHAR(20) NOT NULL,
    routing_key VARCHAR(80) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at DATETIME NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_outbox_event_key (event_key),
    KEY idx_document_outbox_dispatch (status, next_attempt_at, id),
    KEY idx_document_outbox_document_created (document_id, created_at DESC),
    CONSTRAINT fk_document_outbox_document
        FOREIGN KEY (document_id) REFERENCES knowledge_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_outbox_knowledge_base
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_bases (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
