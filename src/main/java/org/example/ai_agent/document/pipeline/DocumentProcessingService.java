package org.example.ai_agent.document.pipeline;

import org.example.ai_agent.document.dto.DocumentProcessingMessage;
import reactor.core.publisher.Mono;

/**
 * 文档处理阶段服务接口。
 * 该接口主要服务于内部异步工作流，分别处理 parse、chunk、embed 等阶段动作。
 */
public interface DocumentProcessingService {
    
    /**
     * 启动文档处理流水线
     * 
     * @param documentId 文档ID
     * @param knowledgeBaseId 知识库ID
     * @param parseMode 解析模式
     * @param enableVectorization 是否启用向量化
     * @return 处理结果
     */
    Mono<Boolean> startDocumentProcessing(Long documentId, Long knowledgeBaseId, 
                                        String parseMode, Boolean enableVectorization);
    
    /**
     * 处理文档解析
     * 
     * @param message 处理消息
     * @return 处理结果
     */
    Mono<Boolean> processDocumentParsing(DocumentProcessingMessage message);
    
    /**
     * 处理文档分块
     * 
     * @param message 处理消息
     * @return 处理结果
     */
    Mono<Boolean> processDocumentChunking(DocumentProcessingMessage message);
    
    /**
     * 处理文档向量化
     * 
     * @param message 处理消息
     * @return 处理结果
     */
    Mono<Boolean> processDocumentEmbedding(DocumentProcessingMessage message);
    
    /**
     * 重新解析文档
     * 
     * @param documentId 文档ID
     * @param parseMode 解析模式
     * @return 处理结果
     */
    Mono<Boolean> reparseDocument(Long documentId, String parseMode);
    
    /**
     * 重新向量化文档
     * 
     * @param documentId 文档ID
     * @return 处理结果
     */
    Mono<Boolean> reEmbedDocument(Long documentId);
    
    /**
     * 发送处理消息到队列
     * 
     * @param message 处理消息
     * @param routingKey 路由键
     * @return 发送结果
     */
    Mono<Boolean> sendProcessingMessage(DocumentProcessingMessage message, String routingKey);
    
    /**
     * 更新文档处理状态
     * 
     * @param documentId 文档ID
     * @param parseStatus 解析状态
     * @param embeddingStatus 向量化状态
     * @param errorMessage 错误信息
     * @return 更新结果
     */
    Mono<Boolean> updateDocumentStatus(Long documentId, String parseStatus, 
                                     String embeddingStatus, String errorMessage);
}
