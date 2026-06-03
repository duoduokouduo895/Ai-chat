-- Flyway schema baseline.
-- Existing environments keep their current tables; fresh environments can start from an empty schema.
-- Real data exports such as sql/ai_agent_db.sql must never be used as migrations.

CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    is_guest TINYINT(1) NULL DEFAULT 0,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    KEY idx_users_created_at (created_at),
    KEY idx_users_guest_last_active_at (is_guest, last_active_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS admin_users (
    admin_id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (admin_id),
    UNIQUE KEY uk_admin_username (username),
    UNIQUE KEY uk_admin_email (email),
    KEY idx_admin_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    user_id BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_conversation_updated_at (updated_at DESC),
    KEY idx_conversation_created_at (created_at DESC),
    KEY idx_conversation_user_updated (user_id, updated_at DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    sender VARCHAR(255) NOT NULL,
    `timestamp` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    model VARCHAR(255) NULL,
    deduplication_key VARCHAR(160) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_deduplication_key (deduplication_key),
    KEY idx_message_conversation_timestamp (conversation_id, `timestamp` DESC),
    KEY idx_message_conv_sender_time (conversation_id, sender, `timestamp` ASC),
    KEY idx_message_sender_timestamp (sender, `timestamp` ASC),
    KEY idx_message_model_timestamp (model, `timestamp` ASC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS knowledge_bases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    creator_id BIGINT NOT NULL,
    is_public TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    document_count INT NOT NULL DEFAULT 0,
    vector_dimension INT NULL,
    embedding_model VARCHAR(50) NULL,
    index_name VARCHAR(100) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_kb_creator_status_created (creator_id, status, created_at DESC),
    KEY idx_kb_public_status_created (is_public, status, created_at DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    knowledge_base_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size BIGINT NOT NULL,
    oss_path VARCHAR(500) NOT NULL,
    oss_url VARCHAR(500) NOT NULL,
    parse_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    content LONGTEXT NULL,
    character_count INT NULL DEFAULT 0,
    chunk_count INT NULL DEFAULT 0,
    is_embedded TINYINT(1) NOT NULL DEFAULT 0,
    embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    metadata JSON NULL,
    uploader_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_documents_kb_created (knowledge_base_id, created_at DESC),
    KEY idx_documents_uploader_created (uploader_id, created_at DESC),
    KEY idx_documents_updated (updated_at DESC),
    KEY idx_documents_type_status (file_type, parse_status),
    CONSTRAINT fk_documents_knowledge_base
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_bases (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_document_chunks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    character_count INT NOT NULL DEFAULT 0,
    metadata JSON NULL,
    embedding_id VARCHAR(100) NULL,
    is_embedded TINYINT(1) NOT NULL DEFAULT 0,
    embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    embedding_error TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chunks_document_index (document_id, chunk_index),
    KEY idx_chunks_document_embedding_status (document_id, embedding_status),
    KEY idx_chunks_knowledge_base_id (knowledge_base_id),
    KEY idx_chunks_embedding_id (embedding_id),
    CONSTRAINT fk_chunks_document
        FOREIGN KEY (document_id) REFERENCES knowledge_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_chunks_knowledge_base
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_bases (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS document_processing_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NULL,
    processing_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    start_time DATETIME NULL,
    end_time DATETIME NULL,
    processing_time_ms BIGINT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    error_stack LONGTEXT NULL,
    processing_params JSON NULL,
    processing_result TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_records_document_type_id (document_id, processing_type, id DESC),
    KEY idx_records_status_created (status, created_at DESC),
    KEY idx_records_knowledge_base_id (knowledge_base_id),
    CONSTRAINT fk_processing_records_document
        FOREIGN KEY (document_id) REFERENCES knowledge_documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_processing_records_knowledge_base
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_bases (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS search_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    query VARCHAR(255) NOT NULL,
    type VARCHAR(50) NULL,
    `timestamp` DATETIME NOT NULL,
    user_id BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_search_history_user_time (user_id, `timestamp` DESC, id DESC),
    KEY idx_search_history_user_query_time (user_id, query(100), `timestamp` DESC),
    KEY idx_search_history_time_id (`timestamp` DESC, id DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
