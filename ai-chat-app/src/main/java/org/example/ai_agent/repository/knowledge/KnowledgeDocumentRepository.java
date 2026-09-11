package org.example.ai_agent.repository.knowledge;

import org.example.ai_agent.dto.document.AdminDocumentSummaryProjection;
import org.example.ai_agent.dto.document.DocumentListProjection;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDocumentStatsProjection;
import org.example.ai_agent.entity.KnowledgeDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 知识库文档数据访问层
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Repository
public interface KnowledgeDocumentRepository extends ReactiveCrudRepository<KnowledgeDocument, Long> {
    @Override
    @Query("SELECT d.* FROM knowledge_documents d INNER JOIN knowledge_bases k ON k.id = d.knowledge_base_id WHERE d.id = :id AND d.deletion_status = 'ACTIVE' AND k.deletion_status = 'ACTIVE'")
    Mono<KnowledgeDocument> findById(Long id);

    
    /**
     * 根据知识库ID查询文档
     * 
     * @param knowledgeBaseId 知识库ID
     * @return 文档列表
     */
    Flux<KnowledgeDocument> findByKnowledgeBaseId(Long knowledgeBaseId);
    
    /**
     * 根据知识库ID查询文档（分页）
     * 
     * @param knowledgeBaseId 知识库ID
     * @param pageable 分页信息
     * @return 文档列表
     */
    Flux<KnowledgeDocument> findByKnowledgeBaseIdOrderByCreatedAtDesc(Long knowledgeBaseId, Pageable pageable);

    Flux<KnowledgeDocument> findByUploaderIdOrderByCreatedAtDesc(Long uploaderId, Pageable pageable);

    /**
     * 查询全站文档（分页）
     *
     * @param pageable 分页信息
     * @return 文档列表
     */
    Flux<KnowledgeDocument> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    @Query("""
            SELECT d.id, d.knowledge_base_id, kb.name AS knowledge_base_name,
                   d.title, d.file_name, d.file_type, d.file_size, d.oss_path, d.oss_url,
                   d.parse_status, d.character_count, d.chunk_count, d.is_embedded,
                   d.embedding_status, d.metadata, d.uploader_id, u.username AS uploader_name,
                   d.created_at, d.updated_at
            FROM knowledge_documents d
            LEFT JOIN knowledge_bases kb ON kb.id = d.knowledge_base_id
            LEFT JOIN users u ON u.user_id = d.uploader_id
            WHERE d.deletion_status = 'ACTIVE' AND kb.deletion_status = 'ACTIVE' AND d.knowledge_base_id = :knowledgeBaseId
            ORDER BY d.created_at DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<DocumentListProjection> findSummariesByKnowledgeBaseId(Long knowledgeBaseId, int limit, long offset);

    @Query("""
            SELECT d.id, d.knowledge_base_id, kb.name AS knowledge_base_name,
                   d.title, d.file_name, d.file_type, d.file_size, d.oss_path, d.oss_url,
                   d.parse_status, d.character_count, d.chunk_count, d.is_embedded,
                   d.embedding_status, d.metadata, d.uploader_id, u.username AS uploader_name,
                   d.created_at, d.updated_at
            FROM knowledge_documents d
            LEFT JOIN knowledge_bases kb ON kb.id = d.knowledge_base_id
            LEFT JOIN users u ON u.user_id = d.uploader_id
            WHERE d.deletion_status = 'ACTIVE' AND kb.deletion_status = 'ACTIVE'
            ORDER BY d.updated_at DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<DocumentListProjection> findAllSummaries(int limit, long offset);

    @Query("""
            SELECT d.id, d.knowledge_base_id, kb.name AS knowledge_base_name,
                   d.title, d.file_name, d.file_type, d.file_size, d.oss_path, d.oss_url,
                   d.parse_status, d.character_count, d.chunk_count, d.is_embedded,
                   d.embedding_status, d.metadata, d.uploader_id, u.username AS uploader_name,
                   d.created_at, d.updated_at
            FROM knowledge_documents d
            LEFT JOIN knowledge_bases kb ON kb.id = d.knowledge_base_id
            LEFT JOIN users u ON u.user_id = d.uploader_id
            WHERE d.deletion_status = 'ACTIVE' AND kb.deletion_status = 'ACTIVE' AND (:keyword IS NULL
                   OR LOWER(COALESCE(d.file_name, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR LOWER(COALESCE(d.title, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR LOWER(COALESCE(kb.name, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR LOWER(COALESCE(u.username, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR CAST(d.id AS CHAR) = :keyword
                   OR CAST(d.uploader_id AS CHAR) = :keyword)
              AND (:parseStatus IS NULL OR d.parse_status = :parseStatus)
              AND (:embeddingStatus IS NULL
                   OR (CASE WHEN d.is_embedded = TRUE THEN 'EMBEDDED' ELSE d.embedding_status END) = :embeddingStatus)
            ORDER BY d.updated_at DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<DocumentListProjection> findAdminPage(String keyword, String parseStatus, String embeddingStatus,
                                               int limit, long offset);

    @Query("""
            SELECT COUNT(*)
            FROM knowledge_documents d
            LEFT JOIN knowledge_bases kb ON kb.id = d.knowledge_base_id
            LEFT JOIN users u ON u.user_id = d.uploader_id
            WHERE d.deletion_status = 'ACTIVE' AND kb.deletion_status = 'ACTIVE' AND (:keyword IS NULL
                   OR LOWER(COALESCE(d.file_name, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR LOWER(COALESCE(d.title, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR LOWER(COALESCE(kb.name, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR LOWER(COALESCE(u.username, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR CAST(d.id AS CHAR) = :keyword
                   OR CAST(d.uploader_id AS CHAR) = :keyword)
              AND (:parseStatus IS NULL OR d.parse_status = :parseStatus)
              AND (:embeddingStatus IS NULL
                   OR (CASE WHEN d.is_embedded = TRUE THEN 'EMBEDDED' ELSE d.embedding_status END) = :embeddingStatus)
            """)
    Mono<Long> countAdminPage(String keyword, String parseStatus, String embeddingStatus);

    @Query("""
            SELECT COUNT(*) AS document_count,
                   COUNT(DISTINCT uploader_id) AS uploader_count,
                   COALESCE(SUM(file_size), 0) AS total_file_size
            FROM knowledge_documents WHERE deletion_status = 'ACTIVE' AND knowledge_base_id IN (SELECT id FROM knowledge_bases WHERE deletion_status = 'ACTIVE')
            """)
    Mono<AdminDocumentSummaryProjection> summarizeForAdmin();

    @Query("""
            SELECT d.id, d.knowledge_base_id, kb.name AS knowledge_base_name,
                   d.title, d.file_name, d.file_type, d.file_size, d.oss_path, d.oss_url,
                   d.parse_status, d.character_count, d.chunk_count, d.is_embedded,
                   d.embedding_status, d.metadata, d.uploader_id, u.username AS uploader_name,
                   d.created_at, d.updated_at
            FROM knowledge_documents d
            LEFT JOIN knowledge_bases kb ON kb.id = d.knowledge_base_id
            LEFT JOIN users u ON u.user_id = d.uploader_id
            WHERE d.deletion_status = 'ACTIVE' AND kb.deletion_status = 'ACTIVE' AND d.uploader_id = :uploaderId
            ORDER BY d.created_at DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<DocumentListProjection> findSummariesByUploaderId(Long uploaderId, int limit, long offset);

    @Query("""
            SELECT COUNT(*) AS document_count,
                   COALESCE(SUM(CASE WHEN parse_status = 'COMPLETED' THEN 1 ELSE 0 END), 0)
                       AS parsed_document_count,
                   COALESCE(SUM(CASE WHEN parse_status = 'FAILED' THEN 1 ELSE 0 END), 0)
                       AS failed_document_count,
                   COALESCE(SUM(CASE WHEN is_embedded = TRUE OR embedding_status = 'COMPLETED'
                                     THEN 1 ELSE 0 END), 0)
                       AS embedded_document_count
            FROM knowledge_documents
            WHERE deletion_status = 'ACTIVE' AND knowledge_base_id = :knowledgeBaseId
            """)
    Mono<KnowledgeBaseDocumentStatsProjection> summarizeByKnowledgeBaseId(Long knowledgeBaseId);
    
    /**
     * 根据解析状态查询文档
     * 
     * @param parseStatus 解析状态
     * @return 文档列表
     */
    Flux<KnowledgeDocument> findByParseStatus(String parseStatus);
    
    /**
     * 根据向量化状态查询文档
     * 
     * @param embeddingStatus 向量化状态
     * @return 文档列表
     */
    Flux<KnowledgeDocument> findByEmbeddingStatus(String embeddingStatus);
    
    /**
     * 根据知识库ID和解析状态查询文档
     * 
     * @param knowledgeBaseId 知识库ID
     * @param parseStatus 解析状态
     * @return 文档列表
     */
    Flux<KnowledgeDocument> findByKnowledgeBaseIdAndParseStatus(Long knowledgeBaseId, String parseStatus);
    
    /**
     * 根据知识库ID统计文档数量
     * 
     * @param knowledgeBaseId 知识库ID
     * @return 文档数量
     */
    @Query("SELECT COUNT(*) FROM knowledge_documents d INNER JOIN knowledge_bases k ON k.id = d.knowledge_base_id WHERE d.knowledge_base_id = :knowledgeBaseId AND d.deletion_status = 'ACTIVE' AND k.deletion_status = 'ACTIVE'")
    Mono<Long> countByKnowledgeBaseId(Long knowledgeBaseId);
    
    /**
     * 根据知识库ID和解析状态统计文档数量
     * 
     * @param knowledgeBaseId 知识库ID
     * @param parseStatus 解析状态
     * @return 文档数量
     */
    @Query("SELECT COUNT(*) FROM knowledge_documents d INNER JOIN knowledge_bases k ON k.id = d.knowledge_base_id WHERE d.knowledge_base_id = :knowledgeBaseId AND d.parse_status = :parseStatus AND d.deletion_status = 'ACTIVE' AND k.deletion_status = 'ACTIVE'")
    Mono<Long> countByKnowledgeBaseIdAndParseStatus(Long knowledgeBaseId, String parseStatus);
    
    /**
     * 根据文件名查询文档
     * 
     * @param fileName 文件名
     * @return 文档
     */
    Mono<KnowledgeDocument> findByFileName(String fileName);
}
