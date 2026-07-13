package org.example.ai_agent.service.impl.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.entity.DocumentProcessingRecord;
import org.example.ai_agent.repository.document.DocumentProcessingRecordRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository;
import org.example.ai_agent.service.DocumentProcessingRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

/**
 * 文档处理记录服务实现类
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Service
public class DocumentProcessingRecordServiceImpl implements DocumentProcessingRecordService {
    
    @Autowired
    private DocumentProcessingRecordRepository recordRepository;
    
    @Autowired
    private KnowledgeDocumentRepository knowledgeDocumentRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${document-processing.claim-timeout-minutes:30}")
    private long claimTimeoutMinutes;
    
    @Override
    public Mono<DocumentProcessingRecord> createProcessingRecord(DocumentProcessingMessage message) {
        // 首先检查文档是否存在，防止外键约束失败
        return knowledgeDocumentRepository.existsById(message.getDocumentId())
            .flatMap(documentExists -> {
                if (!documentExists) {
                    String errorMsg = String.format("Document with id %d does not exist, cannot create processing record", 
                            message.getDocumentId());
                    log.error("创建处理记录时文档不存在: documentId={}, processingType={}",
                            message.getDocumentId(), message.getProcessingType());
                    return Mono.error(new IllegalArgumentException(errorMsg));
                }
                
                return Mono.fromCallable(() -> {
                    DocumentProcessingRecord record = new DocumentProcessingRecord();
                    record.setDocumentId(message.getDocumentId());
                    record.setKnowledgeBaseId(message.getKnowledgeBaseId());
                    record.setProcessingType(message.getProcessingType());
                    record.setStatus(DocumentProcessingConstants.Status.PENDING);
                    record.setRetryCount(0);
                    
                    LocalDateTime now = LocalDateTime.now();
                    record.setCreatedAt(now);
                    record.setUpdatedAt(now);
                    
                    // 序列化处理参数
                    try {
                        String params = objectMapper.writeValueAsString(message);
                        record.setProcessingParams(params);
                    } catch (Exception e) {
                        log.warn("处理参数序列化失败: errorType={}", e.getClass().getSimpleName());
                    }
                    
                    return record;
                })
                .flatMap(recordRepository::save)
                .doOnSuccess(record -> {
                    if (record != null) {
                        log.debug("创建处理记录: documentId={}, processingType={}, recordId={}", 
                                record.getDocumentId(), record.getProcessingType(), record.getId());
                    } else {
                        log.warn("创建处理记录失败: record为null");
                    }
                });
            })
            .onErrorMap(throwable -> {
                if (throwable instanceof IllegalArgumentException) {
                    return throwable;
                }
                log.error("创建处理记录发生异常: documentId={}, processingType={}, errorType={}",
                        message.getDocumentId(), message.getProcessingType(), throwable.getClass().getSimpleName());
                return new RuntimeException("Failed to create processing record", throwable);
            })
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<DocumentProcessingRecord> startProcessing(Long documentId, String processingType) {
        // 首先检查文档是否存在
        return knowledgeDocumentRepository.existsById(documentId)
            .flatMap(documentExists -> {
                if (!documentExists) {
                    String errorMsg = String.format("Document with id %d does not exist, cannot start processing", documentId);
                    log.error("开始处理时文档不存在: documentId={}, processingType={}", documentId, processingType);
                    return Mono.error(new IllegalArgumentException(errorMsg));
                }
                
                return recordRepository.findFirstByDocumentIdAndProcessingTypeOrderByIdDesc(documentId, processingType)
                    .switchIfEmpty(createDefaultRecord(documentId, processingType))
                    .flatMap(record -> {
                        if (record == null) {
                            log.error("无法获取或创建处理记录: documentId={}, processingType={}", documentId, processingType);
                            return Mono.error(new IllegalStateException("Unable to get or create processing record"));
                        }

                        if (DocumentProcessingConstants.Status.COMPLETED.equals(record.getStatus())
                                || hasActiveProcessingClaim(record)) {
                            return Mono.error(new DocumentProcessingAlreadyHandledException(
                                    documentId, processingType, record.getStatus()));
                        }
                        
                        record.setStatus(DocumentProcessingConstants.Status.PROCESSING);
                        record.setStartTime(LocalDateTime.now());
                        record.setUpdatedAt(LocalDateTime.now());
                        
                        return recordRepository.save(record);
                    });
            })
            .doOnSuccess(record -> {
                if (record != null) {
                    log.info("开始处理: documentId={}, processingType={}, recordId={}", 
                            documentId, processingType, record.getId());
                } else {
                    log.warn("开始处理失败: record为null, documentId={}, processingType={}", 
                            documentId, processingType);
                }
            })
            .onErrorMap(throwable -> {
                if (throwable instanceof IllegalArgumentException) {
                    return throwable;
                }
                log.error("开始处理发生异常: documentId={}, processingType={}, errorType={}",
                        documentId, processingType, throwable.getClass().getSimpleName());
                return throwable;
            })
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<DocumentProcessingRecord> completeProcessing(Long documentId, String processingType, String result) {
        return recordRepository.findFirstByDocumentIdAndProcessingTypeOrderByIdDesc(documentId, processingType)
            .switchIfEmpty(Mono.error(new IllegalStateException("Processing record not found for completion")))
            .flatMap(record -> {
                if (record == null) {
                    log.error("无法找到处理记录进行完成操作: documentId={}, processingType={}", documentId, processingType);
                    return Mono.error(new IllegalStateException("Processing record is null"));
                }
                
                LocalDateTime endTime = LocalDateTime.now();
                record.setStatus(DocumentProcessingConstants.Status.COMPLETED);
                record.setEndTime(endTime);
                record.setUpdatedAt(endTime);
                record.setProcessingResult(result);
                
                // 计算处理耗时
                if (record.getStartTime() != null) {
                    long processingTimeMs = java.time.Duration.between(record.getStartTime(), endTime).toMillis();
                    record.setProcessingTimeMs(processingTimeMs);
                }
                
                return recordRepository.save(record);
            })
            .doOnSuccess(record -> {
                if (record != null) {
                    log.info("处理完成: documentId={}, processingType={}, recordId={}, timeMs={}", 
                            documentId, processingType, record.getId(), record.getProcessingTimeMs());
                } else {
                    log.warn("处理完成但record为null: documentId={}, processingType={}", 
                            documentId, processingType);
                }
            })
            .onErrorMap(throwable -> {
                log.error("完成处理发生异常: documentId={}, processingType={}, errorType={}",
                        documentId, processingType, throwable.getClass().getSimpleName());
                return throwable;
            })
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<DocumentProcessingRecord> failProcessing(Long documentId, String processingType, 
                                                       String errorMessage, String errorStack) {
        return recordRepository.findFirstByDocumentIdAndProcessingTypeOrderByIdDesc(documentId, processingType)
            .flatMap(record -> {
                LocalDateTime endTime = LocalDateTime.now();
                record.setStatus(DocumentProcessingConstants.Status.FAILED);
                record.setEndTime(endTime);
                record.setUpdatedAt(endTime);
                record.setErrorMessage(errorMessage);
                record.setErrorStack(errorStack);
                
                // 计算处理耗时
                if (record.getStartTime() != null) {
                    long processingTimeMs = java.time.Duration.between(record.getStartTime(), endTime).toMillis();
                    record.setProcessingTimeMs(processingTimeMs);
                }
                
                return recordRepository.save(record);
            })
            .doOnSuccess(record -> {
                if (record != null) {
                    log.error("处理失败: documentId={}, processingType={}, recordId={}",
                            documentId, processingType, record.getId());
                } else {
                    log.error("处理失败但record为null: documentId={}, processingType={}",
                            documentId, processingType);
                }
            })
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<DocumentProcessingRecord> incrementRetryCount(Long documentId, String processingType) {
        return recordRepository.findFirstByDocumentIdAndProcessingTypeOrderByIdDesc(documentId, processingType)
            .flatMap(record -> {
                record.setRetryCount(record.getRetryCount() + 1);
                record.setStatus(DocumentProcessingConstants.Status.PENDING);
                record.setUpdatedAt(LocalDateTime.now());
                
                return recordRepository.save(record);
            })
            .doOnSuccess(record -> {
                if (record != null) {
                    log.info("增加重试次数: documentId={}, processingType={}, retryCount={}", 
                            documentId, processingType, record.getRetryCount());
                } else {
                    log.warn("增加重试次数失败: record为null, documentId={}, processingType={}", 
                            documentId, processingType);
                }
            })
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<DocumentProcessingRecord> getDocumentProcessingRecords(Long documentId) {
        return recordRepository.findByDocumentId(documentId)
            .doOnSubscribe(s -> log.debug("查询文档处理记录: documentId={}", documentId));
    }
    
    @Override
    public Mono<DocumentProcessingRecord> getProcessingRecord(Long documentId, String processingType) {
        return recordRepository.findFirstByDocumentIdAndProcessingTypeOrderByIdDesc(documentId, processingType)
            .doOnSubscribe(s -> log.debug("查询处理记录: documentId={}, processingType={}", documentId, processingType));
    }
    
    @Override
    public Flux<DocumentProcessingRecord> getFailedProcessingRecords(Integer page, Integer size) {
        final int finalPage = page != null && page >= 0 ? page : 0;
        final int finalSize = size != null && size > 0 && size <= 100 ? size : 10;
        
        return recordRepository.findByStatusOrderByCreatedAtDesc(
                DocumentProcessingConstants.Status.FAILED, 
                PageRequest.of(finalPage, finalSize))
            .doOnSubscribe(s -> log.debug("查询失败处理记录: page={}, size={}", finalPage, finalSize));
    }
    
    @Override
    public Mono<Boolean> cleanupProcessingRecords(Long documentId) {
        return recordRepository.deleteByDocumentId(documentId)
            .map(count -> count > 0)
            .doOnSuccess(result -> log.info("清理处理记录: documentId={}, result={}", documentId, result))
            .onErrorReturn(false);
    }
    
    @Override
    public Mono<ProcessingStatistics> getProcessingStatistics() {
        return Mono.zip(
            recordRepository.countByStatus(DocumentProcessingConstants.Status.PENDING),
            recordRepository.countByStatus(DocumentProcessingConstants.Status.PROCESSING),
            recordRepository.countByStatus(DocumentProcessingConstants.Status.COMPLETED),
            recordRepository.countByStatus(DocumentProcessingConstants.Status.FAILED)
        )
        .map(tuple -> {
            Long pendingCount = tuple.getT1();
            Long processingCount = tuple.getT2();
            Long completedCount = tuple.getT3();
            Long failedCount = tuple.getT4();
            Long totalCount = pendingCount + processingCount + completedCount + failedCount;
            
            return new ProcessingStatistics(pendingCount, processingCount, completedCount, failedCount, totalCount);
        })
        .doOnSuccess(stats -> log.debug("获取处理统计: {}", stats))
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 创建默认处理记录
     */
    private Mono<DocumentProcessingRecord> createDefaultRecord(Long documentId, String processingType) {
        // 在这里不需要再次检查文档存在性，因为调用者已经检查过了
        return Mono.fromCallable(() -> {
            DocumentProcessingRecord record = new DocumentProcessingRecord();
            record.setDocumentId(documentId);
            record.setProcessingType(processingType);
            record.setStatus(DocumentProcessingConstants.Status.PENDING);
            record.setRetryCount(0);
            
            LocalDateTime now = LocalDateTime.now();
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            
            return record;
        })
        .flatMap(recordRepository::save)
        .onErrorMap(throwable -> {
            log.error("创建默认处理记录失败: documentId={}, processingType={}, errorType={}",
                    documentId, processingType, throwable.getClass().getSimpleName());
            return new RuntimeException("Failed to create default processing record", throwable);
        });
    }

    private boolean hasActiveProcessingClaim(DocumentProcessingRecord record) {
        if (!DocumentProcessingConstants.Status.PROCESSING.equals(record.getStatus())) {
            return false;
        }
        if (record.getUpdatedAt() == null) {
            return false;
        }
        return record.getUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(Math.max(1, claimTimeoutMinutes)));
    }
}
