-- Hot-path indexes for existing databases.
-- Every statement is conditional so environments with partial manual tuning can migrate safely.

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'search_history'
          AND index_name = 'idx_search_history_time_id'
    ),
    'SELECT 1',
    'CREATE INDEX idx_search_history_time_id ON search_history (`timestamp` DESC, id DESC)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_documents'
          AND index_name = 'idx_documents_kb_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_documents_kb_created ON knowledge_documents (knowledge_base_id, created_at DESC)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_documents'
          AND index_name = 'idx_documents_uploader_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_documents_uploader_created ON knowledge_documents (uploader_id, created_at DESC)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_documents'
          AND index_name = 'idx_documents_updated'
    ),
    'SELECT 1',
    'CREATE INDEX idx_documents_updated ON knowledge_documents (updated_at DESC)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_document_chunks'
          AND index_name IN ('uk_chunks_document_index', 'idx_chunks_document_index')
    ),
    'SELECT 1',
    'CREATE INDEX idx_chunks_document_index ON knowledge_document_chunks (document_id, chunk_index)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'knowledge_document_chunks'
          AND index_name = 'idx_chunks_document_embedding_status'
    ),
    'SELECT 1',
    'CREATE INDEX idx_chunks_document_embedding_status ON knowledge_document_chunks (document_id, embedding_status)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'document_processing_records'
          AND index_name = 'idx_records_document_type_id'
    ),
    'SELECT 1',
    'CREATE INDEX idx_records_document_type_id ON document_processing_records (document_id, processing_type, id DESC)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'document_processing_records'
          AND index_name = 'idx_records_status_created'
    ),
    'SELECT 1',
    'CREATE INDEX idx_records_status_created ON document_processing_records (status, created_at DESC)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'idx_message_conversation_timestamp'
    ),
    'SELECT 1',
    'CREATE INDEX idx_message_conversation_timestamp ON message (conversation_id, `timestamp`)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'idx_message_conv_sender_time'
    ),
    'SELECT 1',
    'CREATE INDEX idx_message_conv_sender_time ON message (conversation_id, sender, `timestamp` DESC)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'idx_message_sender_timestamp'
    ),
    'SELECT 1',
    'CREATE INDEX idx_message_sender_timestamp ON message (sender, `timestamp` DESC)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'idx_message_model_timestamp'
    ),
    'SELECT 1',
    'CREATE INDEX idx_message_model_timestamp ON message (model, `timestamp` DESC)'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'idx_message_conversation_id'
    ),
    'DROP INDEX idx_message_conversation_id ON message',
    'SELECT 1'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'idx_message_conversation_sender'
    ),
    'DROP INDEX idx_message_conversation_sender ON message',
    'SELECT 1'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'idx_message_conversation_timestamp_asc'
    ),
    'DROP INDEX idx_message_conversation_timestamp_asc ON message',
    'SELECT 1'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'idx_message_sender'
    ),
    'DROP INDEX idx_message_sender ON message',
    'SELECT 1'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'idx_message_model'
    ),
    'DROP INDEX idx_message_model ON message',
    'SELECT 1'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;

SET @flyway_sql = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'message'
          AND index_name = 'idx_message_content_fulltext'
    ),
    'DROP INDEX idx_message_content_fulltext ON message',
    'SELECT 1'
);
PREPARE flyway_stmt FROM @flyway_sql;
EXECUTE flyway_stmt;
DEALLOCATE PREPARE flyway_stmt;
