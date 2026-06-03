package org.example.ai_agent.service;

import org.example.ai_agent.dto.admin.AdminPageDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseCreateDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseStatsDto;
import org.example.ai_agent.entity.KnowledgeBase;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 知识库服务接口
 * <p>
 * 基于LangChain4j 1.0.1的知识库管理服务
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
public interface KnowledgeBaseService {
    
    /**
     * 创建知识库
     * 
     * @param createDto 创建请求
     * @param creatorId 创建者ID
     * @return 创建的知识库
     */
    Mono<KnowledgeBaseDto> createKnowledgeBase(KnowledgeBaseCreateDto createDto, Long creatorId);
    
    /**
     * 获取知识库列表
     * 
     * @param creatorId 当前用户ID
     * @param publicOnly 是否只获取公开知识库
     * @param page 页码
     * @param size 页大小
     * @return 知识库列表
     */
    Flux<KnowledgeBaseDto> getKnowledgeBases(Long creatorId, Boolean publicOnly, Integer page, Integer size);

    /**
     * 管理员视角获取全站知识库列表
     *
     * @param page 页码
     * @param size 页大小
     * @return 全站知识库列表
     */
    Flux<KnowledgeBaseDto> getAllKnowledgeBases(Integer page, Integer size);

    Mono<AdminPageDto<KnowledgeBaseDto>> getAllKnowledgeBasesPage(Integer page, Integer size, String keyword);

    /**
     * 管理员视角获取指定用户创建的知识库
     *
     * @param creatorId 用户ID
     * @param page 页码
     * @param size 页大小
     * @return 知识库列表
     */
    Flux<KnowledgeBaseDto> getKnowledgeBasesByCreatorId(Long creatorId, Integer page, Integer size);
    
    /**
     * 获取知识库详情
     * 
     * @param id 知识库ID
     * @return 知识库详情
     */
    Mono<KnowledgeBaseDto> getKnowledgeBase(Long id);

    Mono<KnowledgeBaseDto> getKnowledgeBase(Long id, Long requesterUserId, boolean isAdmin);

    Mono<KnowledgeBaseStatsDto> getKnowledgeBaseStats(Long id, Long requesterUserId, boolean isAdmin);
    
    /**
     * 更新知识库
     * 
     * @param id 知识库ID
     * @param updateDto 更新请求
     * @return 更新后的知识库
     */
    Mono<KnowledgeBaseDto> updateKnowledgeBase(Long id, KnowledgeBaseCreateDto updateDto,
                                               Long requesterUserId, boolean isAdmin);
    
    /**
     * 删除知识库
     * 
     * @param id 知识库ID
     * @return 删除结果
     */
    Mono<Void> deleteKnowledgeBase(Long id, Long requesterUserId, boolean isAdmin);
    
    /**
     * 检查知识库是否存在
     * 
     * @param id 知识库ID
     * @return 是否存在
     */
    Mono<Boolean> existsById(Long id);

    Mono<KnowledgeBase> getAuthorizedKnowledgeBase(Long id, Long requesterUserId,
                                                   boolean isAdmin, boolean requireWriteAccess);
    
    /**
     * 更新知识库文档数量
     * 
     * @param id 知识库ID
     * @param documentCount 文档数量
     * @return 更新结果
     */
    Mono<KnowledgeBase> updateDocumentCount(Long id, Integer documentCount);

    /**
     * 原子增减知识库文档数量，避免并发上传或删除时发生计数覆盖。
     */
    Mono<Void> adjustDocumentCount(Long id, Integer delta);
    
    /**
     * 转换实体为DTO
     * 
     * @param entity 知识库实体
     * @return 知识库DTO
     */
    KnowledgeBaseDto toDto(KnowledgeBase entity);
    
    /**
     * 转换DTO为实体
     * 
     * @param createDto 创建DTO
     * @param creatorId 创建者ID
     * @return 知识库实体
     */
    KnowledgeBase toEntity(KnowledgeBaseCreateDto createDto, Long creatorId);
}
