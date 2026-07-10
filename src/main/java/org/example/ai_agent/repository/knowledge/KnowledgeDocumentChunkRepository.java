package org.example.ai_agent.repository.knowledge;

import org.example.ai_agent.entity.KnowledgeDocumentChunk;
import org.example.ai_agent.dto.knowledge.KnowledgeChunkIndexProjection;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 知识库文档块数据访问层
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Repository
public interface KnowledgeDocumentChunkRepository extends ReactiveCrudRepository<KnowledgeDocumentChunk, Long> {
    
    /**
     * 根据文档ID查询文档块（按序号排序）
     * 
     * @param documentId 文档ID
     * @return 文档块列表
     */
    Flux<KnowledgeDocumentChunk> findByDocumentIdOrderByChunkIndex(Long documentId);
    
    /**
     * 根据文档ID和向量化状态查询文档块
     * 
     * @param documentId 文档ID
     * @param embeddingStatus 向量化状态
     * @return 文档块列表
     */
    Flux<KnowledgeDocumentChunk> findByDocumentIdAndEmbeddingStatus(Long documentId, String embeddingStatus);
    
    /**
     * 根据知识库ID查询文档块
     * 
     * @param knowledgeBaseId 知识库ID
     * @return 文档块列表
     */
    Flux<KnowledgeDocumentChunk> findByKnowledgeBaseId(Long knowledgeBaseId);
    
    /**
     * 根据向量化状态查询文档块
     * 
     * @param embeddingStatus 向量化状态
     * @return 文档块列表
     */
    Flux<KnowledgeDocumentChunk> findByEmbeddingStatus(String embeddingStatus);
    
    /**
     * 根据是否已向量化查询文档块
     * 
     * @param isEmbedded 是否已向量化
     * @return 文档块列表
     */
    Flux<KnowledgeDocumentChunk> findByIsEmbedded(Boolean isEmbedded);
    
    /**
     * 根据文档ID删除文档块
     * 
     * @param documentId 文档ID
     * @return 删除数量
     */
    Mono<Long> deleteByDocumentId(Long documentId);
    
    /**
     * 根据知识库ID删除文档块
     * 
     * @param knowledgeBaseId 知识库ID
     * @return 删除数量
     */
    Mono<Long> deleteByKnowledgeBaseId(Long knowledgeBaseId);
    
    /**
     * 根据文档ID统计文档块数量
     * 
     * @param documentId 文档ID
     * @return 文档块数量
     */
    Mono<Long> countByDocumentId(Long documentId);
    
    /**
     * 根据文档ID和是否已向量化统计文档块数量
     * 
     * @param documentId 文档ID
     * @param isEmbedded 是否已向量化
     * @return 文档块数量
     */
    Mono<Long> countByDocumentIdAndIsEmbedded(Long documentId, Boolean isEmbedded);
    
    /**
     * 根据知识库ID统计文档块数量
     * 
     * @param knowledgeBaseId 知识库ID
     * @return 文档块数量
     */
    Mono<Long> countByKnowledgeBaseId(Long knowledgeBaseId);
    
    /**
     * 根据知识库ID和向量化状态统计文档块数量
     * 
     * @param knowledgeBaseId 知识库ID
     * @param embeddingStatus 向量化状态
     * @return 文档块数量
     */
    Mono<Long> countByKnowledgeBaseIdAndEmbeddingStatus(Long knowledgeBaseId, String embeddingStatus);
    
    /**
     * 根据向量ID查询文档块
     * 
     * @param embeddingId 向量ID
     * @return 文档块
     */
    Mono<KnowledgeDocumentChunk> findByEmbeddingId(String embeddingId);

    @Query("""
            SELECT c.id AS chunk_id,
                   c.document_id,
                   c.knowledge_base_id,
                   c.chunk_index,
                   c.content,
                   d.title AS document_title,
                   kb.name AS knowledge_base_name,
                   kb.creator_id AS knowledge_base_creator_id,
                   kb.is_public AS knowledge_base_is_public,
                   d.uploader_id AS document_uploader_id,
                   kb.status AS knowledge_base_status,
                   d.parse_status AS document_parse_status,
                   d.embedding_status AS document_embedding_status,
                   c.embedding_status AS chunk_embedding_status,
                   c.created_at,
                   c.updated_at
            FROM knowledge_document_chunks c
            JOIN knowledge_documents d ON d.id = c.document_id
            JOIN knowledge_bases kb ON kb.id = c.knowledge_base_id
            WHERE c.document_id = :documentId
            ORDER BY c.chunk_index ASC
            """)
    Flux<KnowledgeChunkIndexProjection> findIndexRowsByDocumentId(Long documentId);

    @Query("""
            SELECT c.id AS chunk_id,
                   c.document_id,
                   c.knowledge_base_id,
                   c.chunk_index,
                   c.content,
                   d.title AS document_title,
                   kb.name AS knowledge_base_name,
                   kb.creator_id AS knowledge_base_creator_id,
                   kb.is_public AS knowledge_base_is_public,
                   d.uploader_id AS document_uploader_id,
                   kb.status AS knowledge_base_status,
                   d.parse_status AS document_parse_status,
                   d.embedding_status AS document_embedding_status,
                   c.embedding_status AS chunk_embedding_status,
                   c.created_at,
                   c.updated_at
            FROM knowledge_document_chunks c
            JOIN knowledge_documents d ON d.id = c.document_id
            JOIN knowledge_bases kb ON kb.id = c.knowledge_base_id
            WHERE c.knowledge_base_id = :knowledgeBaseId
            ORDER BY c.document_id ASC, c.chunk_index ASC
            """)
    Flux<KnowledgeChunkIndexProjection> findIndexRowsByKnowledgeBaseId(Long knowledgeBaseId);

    @Query("""
            SELECT c.id AS chunk_id,
                   c.document_id,
                   c.knowledge_base_id,
                   c.chunk_index,
                   c.content,
                   d.title AS document_title,
                   kb.name AS knowledge_base_name,
                   kb.creator_id AS knowledge_base_creator_id,
                   kb.is_public AS knowledge_base_is_public,
                   d.uploader_id AS document_uploader_id,
                   kb.status AS knowledge_base_status,
                   d.parse_status AS document_parse_status,
                   d.embedding_status AS document_embedding_status,
                   c.embedding_status AS chunk_embedding_status,
                   c.created_at,
                   c.updated_at
            FROM knowledge_document_chunks c
            JOIN knowledge_documents d ON d.id = c.document_id
            JOIN knowledge_bases kb ON kb.id = c.knowledge_base_id
            ORDER BY c.knowledge_base_id ASC, c.document_id ASC, c.chunk_index ASC
            """)
    Flux<KnowledgeChunkIndexProjection> findAllIndexRows();
}
