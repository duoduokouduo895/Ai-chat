package org.example.ai_agent.service;

import org.example.ai_agent.dto.document.DocumentDto;
import org.example.ai_agent.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文档服务接口
 * <p>
 * 基于LangChain4j 1.0.1的文档处理服务
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
public interface DocumentService {
    
    /**
     * 上传文档
     * 
     * @param file 文件
     * @param knowledgeBaseId 知识库ID
     * @param parseMode 解析模式
     * @param enableVectorization 是否启用向量化
     * @param uploaderId 上传者ID
     * @return 文档信息
     */
    Mono<DocumentDto> uploadDocument(MultipartFile file, Long knowledgeBaseId, 
                                   String parseMode, Boolean enableVectorization, Long uploaderId);
    
    /**
     * 获取知识库的文档列表
     * 
     * @param knowledgeBaseId 知识库ID
     * @param page 页码
     * @param size 页大小
     * @return 文档列表
     */
    Flux<DocumentDto> getDocuments(Long knowledgeBaseId, Integer page, Integer size);
    
    /**
     * 获取文档详情
     * 
     * @param id 文档ID
     * @return 文档详情
     */
    Mono<DocumentDto> getDocument(Long id);
    
    /**
     * 删除文档
     * 
     * @param id 文档ID
     * @return 删除结果
     */
    Mono<Void> deleteDocument(Long id);
    
    /**
     * 重新解析文档
     * 
     * @param id 文档ID
     * @return 文档信息
     */
    Mono<DocumentDto> reparseDocument(Long id);
    
    /**
     * 重新向量化文档
     * 
     * @param id 文档ID
     * @return 文档信息
     */
    Mono<DocumentDto> reEmbedDocument(Long id);
    
    /**
     * 检查文件类型是否支持
     * 
     * @param fileName 文件名
     * @return 是否支持
     */
    boolean isSupportedFileType(String fileName);
    
    /**
     * 转换实体为DTO
     * 
     * @param entity 文档实体
     * @return 文档DTO
     */
    DocumentDto toDto(KnowledgeDocument entity);
}