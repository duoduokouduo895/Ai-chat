package org.example.ai_agent.repository.document;

import org.example.ai_agent.entity.DocumentProcessingRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文档处理记录数据访问层
 *
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Repository
public interface DocumentProcessingRecordRepository extends ReactiveCrudRepository<DocumentProcessingRecord, Long> {

    /**
     * 根据文档ID查询处理记录
     *
     * @param documentId 文档ID
     * @return 处理记录列表
     */
    Flux<DocumentProcessingRecord> findByDocumentId(Long documentId);

    /**
     * 根据文档ID和处理类型查询处理记录
     *
     * <p>同一文档的同一处理类型可能存在多条记录（如重试场景），
     * 因此使用 Flux 而非 Mono，避免 NonUniqueResultException。</p>
     *
     * @param documentId 文档ID
     * @param processingType 处理类型
     * @return 处理记录列表
     */
    Flux<DocumentProcessingRecord> findByDocumentIdAndProcessingType(Long documentId, String processingType);

    /**
     * 根据文档ID和处理类型查询最新的处理记录
     *
     * @param documentId 文档ID
     * @param processingType 处理类型
     * @return 最新的处理记录
     */
    Mono<DocumentProcessingRecord> findFirstByDocumentIdAndProcessingTypeOrderByIdDesc(Long documentId, String processingType);
    
    /**
     * 根据状态查询处理记录
     * 
     * @param status 处理状态
     * @param pageable 分页信息
     * @return 处理记录列表
     */
    Flux<DocumentProcessingRecord> findByStatus(String status, Pageable pageable);
    
    /**
     * 根据知识库ID查询处理记录
     * 
     * @param knowledgeBaseId 知识库ID
     * @return 处理记录列表
     */
    Flux<DocumentProcessingRecord> findByKnowledgeBaseId(Long knowledgeBaseId);
    
    /**
     * 根据处理类型查询处理记录
     * 
     * @param processingType 处理类型
     * @return 处理记录列表
     */
    Flux<DocumentProcessingRecord> findByProcessingType(String processingType);
    
    /**
     * 查询失败的处理记录
     * 
     * @param pageable 分页信息
     * @return 处理记录列表
     */
    Flux<DocumentProcessingRecord> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    
    /**
     * 根据文档ID删除处理记录
     * 
     * @param documentId 文档ID
     * @return 删除数量
     */
    Mono<Long> deleteByDocumentId(Long documentId);
    
    /**
     * 统计各状态的处理记录数量
     * 
     * @param status 处理状态
     * @return 记录数量
     */
    Mono<Long> countByStatus(String status);
    
    /**
     * 统计文档的处理记录数量
     * 
     * @param documentId 文档ID
     * @return 记录数量
     */
    Mono<Long> countByDocumentId(Long documentId);
}
