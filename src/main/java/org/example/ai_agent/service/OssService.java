package org.example.ai_agent.service;

import reactor.core.publisher.Mono;

import java.io.InputStream;

/**
 * 阿里云OSS服务接口
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
public interface OssService {
    
    /**
     * 上传文件到OSS
     * 
     * @param fileName 文件名
     * @param inputStream 文件输入流
     * @param fileSize 文件大小
     * @param contentType 文件类型
     * @return OSS文件路径
     */
    Mono<String> uploadFile(String fileName, InputStream inputStream, Long fileSize, String contentType);
    
    /**
     * 上传知识库文档
     * 
     * @param knowledgeBaseId 知识库ID
     * @param fileName 文件名
     * @param inputStream 文件输入流
     * @param fileSize 文件大小
     * @param contentType 文件类型
     * @return OSS文件信息
     */
    Mono<OssFileInfo> uploadKnowledgeDocument(Long knowledgeBaseId, String fileName, 
                                            InputStream inputStream, Long fileSize, String contentType);
    
    /**
     * 获取文件访问URL
     * 
     * @param ossPath OSS文件路径
     * @return 访问URL
     */
    String getFileUrl(String ossPath);
    
    /**
     * 删除文件
     * 
     * @param ossPath OSS文件路径
     * @return 删除结果
     */
    Mono<Boolean> deleteFile(String ossPath);
    
    /**
     * 获取文件输入流
     * 
     * @param ossPath OSS文件路径
     * @return 文件输入流
     */
    Mono<InputStream> getFileInputStream(String ossPath);
    
    /**
     * OSS文件信息
     */
    record OssFileInfo(String ossPath, String url, Long fileSize, String contentType) {}
}