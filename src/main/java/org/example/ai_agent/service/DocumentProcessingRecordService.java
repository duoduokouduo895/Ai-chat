package org.example.ai_agent.service;

import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.entity.DocumentProcessingRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文档处理记录服务接口
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
public interface DocumentProcessingRecordService {
    
    /**
     * 创建处理记录
     * 
     * @param message 处理消息
     * @return 处理记录
     */
    Mono<DocumentProcessingRecord> createProcessingRecord(DocumentProcessingMessage message);
    
    /**
     * 开始处理
     * 
     * @param documentId 文档ID
     * @param processingType 处理类型
     * @return 更新结果
     */
    Mono<DocumentProcessingRecord> startProcessing(Long documentId, String processingType);
    
    /**
     * 完成处理
     * 
     * @param documentId 文档ID
     * @param processingType 处理类型
     * @param result 处理结果
     * @return 更新结果
     */
    Mono<DocumentProcessingRecord> completeProcessing(Long documentId, String processingType, String result);
    
    /**
     * 处理失败
     * 
     * @param documentId 文档ID
     * @param processingType 处理类型
     * @param errorMessage 错误信息
     * @param errorStack 错误堆栈
     * @return 更新结果
     */
    Mono<DocumentProcessingRecord> failProcessing(Long documentId, String processingType, 
                                                String errorMessage, String errorStack);
    
    /**
     * 增加重试次数
     * 
     * @param documentId 文档ID
     * @param processingType 处理类型
     * @return 更新结果
     */
    Mono<DocumentProcessingRecord> incrementRetryCount(Long documentId, String processingType);
    
    /**
     * 获取文档的处理记录
     * 
     * @param documentId 文档ID
     * @return 处理记录列表
     */
    Flux<DocumentProcessingRecord> getDocumentProcessingRecords(Long documentId);
    
    /**
     * 获取文档特定类型的处理记录
     * 
     * @param documentId 文档ID
     * @param processingType 处理类型
     * @return 处理记录
     */
    Mono<DocumentProcessingRecord> getProcessingRecord(Long documentId, String processingType);
    
    /**
     * 获取失败的处理记录
     * 
     * @param page 页码
     * @param size 页大小
     * @return 处理记录列表
     */
    Flux<DocumentProcessingRecord> getFailedProcessingRecords(Integer page, Integer size);
    
    /**
     * 清理文档的处理记录
     * 
     * @param documentId 文档ID
     * @return 删除结果
     */
    Mono<Boolean> cleanupProcessingRecords(Long documentId);
    
    /**
     * 获取处理统计信息
     * 
     * @return 统计信息
     */
    Mono<ProcessingStatistics> getProcessingStatistics();
    
    /**
     * 处理统计信息
     */
    record ProcessingStatistics(
        Long pendingCount,
        Long processingCount,
        Long completedCount,
        Long failedCount,
        Long totalCount
    ) {}
}