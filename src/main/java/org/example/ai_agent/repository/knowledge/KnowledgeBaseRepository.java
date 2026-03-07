package org.example.ai_agent.repository.knowledge;

import org.example.ai_agent.entity.KnowledgeBase;
import org.springframework.data.domain.Pageable;
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
     * @param pageable 分页信息
     * @return 知识库列表
     */
    Flux<KnowledgeBase> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    
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