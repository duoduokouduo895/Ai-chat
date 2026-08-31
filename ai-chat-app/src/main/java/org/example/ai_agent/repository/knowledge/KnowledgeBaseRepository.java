package org.example.ai_agent.repository.knowledge;

import org.example.ai_agent.dto.knowledge.AdminKnowledgeBaseSummaryProjection;
import org.example.ai_agent.entity.KnowledgeBase;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 知识库数据访问层
 *
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Repository
public interface KnowledgeBaseRepository extends ReactiveCrudRepository<KnowledgeBase, Long> {

    /**
     * 根据创建者ID查询知识库
     *
     * @param creatorId 创建者ID
     * @return 知识库列表
     */
    Flux<KnowledgeBase> findByCreatorId(Long creatorId);

    Flux<KnowledgeBase> findByCreatorIdOrderByCreatedAtDesc(Long creatorId, Pageable pageable);
    
    /**
     * 根据是否公开查询知识库
     *
     * @param isPublic 是否公开
     * @param pageable 分页信息
     * @return 知识库列表
     */
    Flux<KnowledgeBase> findByIsPublic(Boolean isPublic, Pageable pageable);
    
    /**
     * 根据状态查询知识库
     *
     * @param status 状态
     * @return 知识库列表
     */
    Flux<KnowledgeBase> findByStatus(String status);
    
    /**
     * 根据名称查询知识库
     *
     * @param name 知识库名称
     * @return 知识库
     */
    Mono<KnowledgeBase> findByName(String name);
    
    /**
     * 查询活跃状态的知识库
     *
     * @param status 状态
     * @param pageable 分页信息
     * @return 知识库列表
     */
    Flux<KnowledgeBase> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    @Query("""
            SELECT *
            FROM knowledge_bases
            WHERE status = 'ACTIVE'
              AND (:keyword IS NULL
                   OR LOWER(name) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR LOWER(COALESCE(description, '')) LIKE CONCAT('%', LOWER(:keyword), '%'))
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<KnowledgeBase> findAdminPage(String keyword, int limit, long offset);

    @Query("""
            SELECT COUNT(*)
            FROM knowledge_bases
            WHERE status = 'ACTIVE'
              AND (:keyword IS NULL
                   OR LOWER(name) LIKE CONCAT('%', LOWER(:keyword), '%')
                   OR LOWER(COALESCE(description, '')) LIKE CONCAT('%', LOWER(:keyword), '%'))
            """)
    Mono<Long> countAdminPage(String keyword);

    @Query("""
            SELECT COUNT(*) AS knowledge_base_count,
                   COALESCE(SUM(document_count), 0) AS document_count,
                   COALESCE(SUM(CASE WHEN is_public = TRUE THEN 1 ELSE 0 END), 0)
                       AS public_knowledge_base_count
            FROM knowledge_bases
            WHERE status = 'ACTIVE'
            """)
    Mono<AdminKnowledgeBaseSummaryProjection> summarizeForAdmin();
    
    /**
     * 查询公开的活跃知识库
     *
     * @param isPublic 是否公开
     * @param status 状态
     * @param pageable 分页信息
     * @return 知识库列表
     */
    Flux<KnowledgeBase> findByIsPublicAndStatusOrderByCreatedAtDesc(Boolean isPublic, String status, Pageable pageable);
    
    /**
     * 查询指定用户创建的或公开的活跃知识库
     *
     * <p>原 findByCreatorIdOrIsPublicAndStatus… 派生方法名存在运算符优先级歧义，
     * 使用显式 @Query 将业务边界固定为
     * ((creator_id = ?) OR (is_public = ?)) AND (status = ?)，
     * 避免拥有者看到已归档或已删除的知识库。</p>
     *
     * @param creatorId 创建者ID
     * @param isPublic 是否公开
     * @param status 状态
     * @param pageable 分页信息
     * @return 知识库列表
     */
    @Query("""
            SELECT *
            FROM (
                SELECT *
                FROM knowledge_bases
                WHERE creator_id = :creatorId AND status = :status
                UNION
                SELECT *
                FROM knowledge_bases
                WHERE is_public = :isPublic AND status = :status
            ) kb
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<KnowledgeBase> findByCreatorIdOrPublicAndStatus(Long creatorId, Boolean isPublic, String status,
                                                         int limit, long offset);

    @Query("UPDATE knowledge_bases SET document_count = GREATEST(document_count + :delta, 0) WHERE id = :id")
    Mono<Integer> adjustDocumentCount(Long id, Integer delta);

    /**
     * 统计知识库数量
     * 
     * @param status 状态
     * @return 数量
     */
    Mono<Long> countByStatus(String status);
    
    /**
     * 统计公开知识库数量
     * 
     * @param isPublic 是否公开
     * @param status 状态
     * @return 数量
     */
    Mono<Long> countByIsPublicAndStatus(Boolean isPublic, String status);
}
