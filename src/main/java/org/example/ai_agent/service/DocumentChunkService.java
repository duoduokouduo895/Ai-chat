package org.example.ai_agent.service;

import org.example.ai_agent.dto.document.DocumentChunkDto;
import org.example.ai_agent.entity.KnowledgeDocumentChunk;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 文档块处理服务接口
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
public interface DocumentChunkService {
    
    /**
     * 创建文档块
     * 
     * @param documentId 文档ID
     * @param knowledgeBaseId 知识库ID
     * @param content 文档内容
     * @return 文档块列表
     */
    Mono<List<DocumentChunkDto>> createDocumentChunks(Long documentId, Long knowledgeBaseId, String content);
    
    /**
     * 获取文档的所有块
     * 
     * @param documentId 文档ID
     * @return 文档块列表
     */
    Flux<DocumentChunkDto> getDocumentChunks(Long documentId);
    
    /**
     * 获取待向量化的文档块
     * 
     * @param documentId 文档ID
     * @return 文档块列表
     */
    Flux<DocumentChunkDto> getPendingEmbeddingChunks(Long documentId);
    
    /**
     * 更新文档块向量化状态
     * 
     * @param chunkId 文档块ID
     * @param embeddingId 向量ID
     * @param status 向量化状态
     * @param errorMessage 错误信息
     * @return 更新结果
     */
    Mono<Boolean> updateChunkEmbeddingStatus(Long chunkId, String embeddingId, String status, String errorMessage);
    
    /**
     * 批量更新文档块向量化状态
     * 
     * @param chunkIds 文档块ID列表
     * @param embeddingIds 向量ID列表
     * @param status 向量化状态
     * @return 更新结果
     */
    Mono<Boolean> batchUpdateChunkEmbeddingStatus(List<Long> chunkIds, List<String> embeddingIds, String status);
    
    /**
     * 删除文档的所有块
     * 
     * @param documentId 文档ID
     * @return 删除结果
     */
    Mono<Boolean> deleteDocumentChunks(Long documentId);
    
    /**
     * 统计文档块数量
     * 
     * @param documentId 文档ID
     * @return 块数量
     */
    Mono<Integer> countDocumentChunks(Long documentId);
    
    /**
     * 统计已向量化的文档块数量
     * 
     * @param documentId 文档ID
     * @return 已向量化块数量
     */
    Mono<Integer> countEmbeddedChunks(Long documentId);
    
    /**
     * 转换实体为DTO
     * 
     * @param entity 文档块实体
     * @return 文档块DTO
     */
    DocumentChunkDto toDto(KnowledgeDocumentChunk entity);
}