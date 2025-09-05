package org.example.ai_agent.repository.knowledge;

import org.example.ai_agent.entity.KnowledgeDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 知识库文档数据访问层
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
@Repository
public interface KnowledgeDocumentRepository extends ReactiveCrudRepository<KnowledgeDocument, Long> {
    
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
    Mono<Long> countByKnowledgeBaseId(Long knowledgeBaseId);
    
    /**
     * 根据知识库ID和解析状态统计文档数量
     * 
     * @param knowledgeBaseId 知识库ID
     * @param parseStatus 解析状态
     * @return 文档数量
     */
    Mono<Long> countByKnowledgeBaseIdAndParseStatus(Long knowledgeBaseId, String parseStatus);
    
    /**
     * 根据文件名查询文档
     * 
     * @param fileName 文件名
     * @return 文档
     */
    Mono<KnowledgeDocument> findByFileName(String fileName);
}