package org.example.ai_agent.service.impl.document;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository;
import org.example.ai_agent.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 文档处理工作流服务实现类
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Service
public class DocumentProcessingServiceImpl implements DocumentProcessingService {

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentChunkService documentChunkService;
    private final EmbeddingService embeddingService;
    private final DocumentProcessingRecordService recordService;
    private final KnowledgeChunkSearchService knowledgeChunkSearchService;
    private final DocumentProcessingPublisher publisher;
    private final DocumentProcessingStages stages;
    private final DocumentEmbeddingBatchProcessor embeddingBatchProcessor;
    private final int embeddingBatchSize;

    public DocumentProcessingServiceImpl(KnowledgeDocumentRepository documentRepository,
                                         DocumentChunkService documentChunkService,
                                         EmbeddingService embeddingService,
                                         DocumentProcessingRecordService recordService,
                                         KnowledgeChunkSearchService knowledgeChunkSearchService,
                                         DocumentProcessingPublisher publisher,
                                         DocumentProcessingStages stages,
                                         DocumentEmbeddingBatchProcessor embeddingBatchProcessor,
                                         @Value("${knowledge-base.embedding.batch-size:32}") int embeddingBatchSize) {
        this.documentRepository = documentRepository;
        this.documentChunkService = documentChunkService;
        this.embeddingService = embeddingService;
        this.recordService = recordService;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
        this.publisher = publisher;
        this.stages = stages;
        this.embeddingBatchProcessor = embeddingBatchProcessor;
        this.embeddingBatchSize = embeddingBatchSize;
    }
    
    @Override
    public Mono<Boolean> startDocumentProcessing(Long documentId, Long knowledgeBaseId, 
                                               String parseMode, Boolean enableVectorization) {
        log.info("启动文档处理流水线: documentId={}, knowledgeBaseId={}, parseMode={}, enableVectorization={}", 
                documentId, knowledgeBaseId, parseMode, enableVectorization);
        
        // 创建解析消息并发送到队列
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(
            documentId, knowledgeBaseId, parseMode, enableVectorization);
        
        return sendProcessingMessage(message, DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE);
    }
    
    @Override
    public Mono<Boolean> processDocumentParsing(DocumentProcessingMessage message) {
        return recordService.startProcessing(message.getDocumentId(), message.getProcessingType())
            .then(documentRepository.findById(message.getDocumentId()))
            .flatMap(document -> {
                log.info("开始处理文档解析: documentId={}, parseMode={}", 
                        message.getDocumentId(), message.getParseMode());
                
                // 更新状态为处理中
                return updateDocumentStatus(message.getDocumentId(), 
                        DocumentProcessingConstants.Status.PROCESSING, null, null)
                    .then(stages.parseDocumentFromOss(document, message.getParseMode()))
                    .flatMap(parsedContent -> {
                        // 更新文档内容和状态
                        document.setContent(parsedContent);
                        document.setCharacterCount(parsedContent.length());
                        document.setParseStatus(DocumentProcessingConstants.Status.COMPLETED);
                        document.setUpdatedAt(LocalDateTime.now());
                        
                        return documentRepository.save(document);
                    })
                    .flatMap(savedDocument -> {
                        // 发送分块消息，携带向量化选项
                        DocumentProcessingMessage chunkMessage = DocumentProcessingMessage.createChunkMessage(
                            message.getDocumentId(), message.getKnowledgeBaseId(), message.getEnableVectorization());
                        
                        return sendProcessingMessage(chunkMessage, 
                            DocumentProcessingConstants.RoutingKey.DOCUMENT_CHUNK)
                            .flatMap(sent -> sent
                                    ? recordService.completeProcessing(
                                            message.getDocumentId(),
                                            message.getProcessingType(),
                                            "Content length: " + savedDocument.getContent().length())
                                        .thenReturn(true)
                                    : Mono.just(false));
                    });
            })
            .doOnSuccess(result -> log.info("文档解析处理完成: documentId={}, result={}", 
                    message.getDocumentId(), result))
            .doOnError(error -> log.error("文档解析处理失败: documentId={}, error={}",
                    message.getDocumentId(), error.getMessage(), error));
    }
    
    @Override
    public Mono<Boolean> processDocumentChunking(DocumentProcessingMessage message) {
        return recordService.startProcessing(message.getDocumentId(), message.getProcessingType())
            .then(documentRepository.findById(message.getDocumentId()))
            .flatMap(document -> {
                log.info("开始处理文档分块: documentId={}", message.getDocumentId());
                
                if (document.getContent() == null || document.getContent().trim().isEmpty()) {
                    log.warn("文档内容为空，跳过分块处理: documentId={}", message.getDocumentId());
                    return recordService.completeProcessing(
                            message.getDocumentId(), 
                            message.getProcessingType(), 
                            "Skipped - empty content")
                        .then(Mono.just(true));
                }
                
                return stages.createChunksAndIndex(message, document)
                    .flatMap(chunkCount -> {
                        // 更新文档块数量
                        document.setChunkCount(chunkCount);
                        document.setUpdatedAt(LocalDateTime.now());
                        
                        return documentRepository.save(document).thenReturn(chunkCount);
                    })
                    .flatMap(chunkCount -> {
                        // 如果启用向量化，发送向量化消息
                        if (Boolean.TRUE.equals(message.getEnableVectorization())) {
                            DocumentProcessingMessage embedMessage = DocumentProcessingMessage.createEmbedMessage(
                                message.getDocumentId(), message.getKnowledgeBaseId());
                            
                            return sendProcessingMessage(embedMessage, 
                                DocumentProcessingConstants.RoutingKey.DOCUMENT_EMBED)
                                .flatMap(sent -> sent
                                        ? recordService.completeProcessing(
                                                message.getDocumentId(),
                                                message.getProcessingType(),
                                                "Chunks created: " + chunkCount)
                                            .thenReturn(true)
                                        : Mono.just(false));
                        }
                        return recordService.completeProcessing(
                                message.getDocumentId(),
                                message.getProcessingType(),
                                "Chunks created: " + chunkCount)
                            .thenReturn(true);
                    });
            })
            .doOnSuccess(result -> log.info("文档分块处理完成: documentId={}, result={}", 
                    message.getDocumentId(), result))
            .doOnError(error -> log.error("文档分块处理失败: documentId={}, error={}",
                    message.getDocumentId(), error.getMessage(), error));
    }
    
    @Override
    public Mono<Boolean> processDocumentEmbedding(DocumentProcessingMessage message) {
        return recordService.startProcessing(message.getDocumentId(), message.getProcessingType())
            .thenMany(documentChunkService.getPendingEmbeddingChunks(message.getDocumentId()))
            .buffer(effectiveEmbeddingBatchSize())
            .switchOnFirst((signal, batches) -> {
                if (!signal.hasValue()) {
                    log.info("没有待向量化的文档块: documentId={}", message.getDocumentId());
                    return Flux.empty();
                }

                log.info("开始分批处理文档向量化: documentId={}, batchSize={}",
                        message.getDocumentId(), effectiveEmbeddingBatchSize());

                return updateDocumentStatus(message.getDocumentId(), null, 
                        DocumentProcessingConstants.Status.PROCESSING, null)
                    .then(embeddingBatchProcessor.resolveEmbeddingProfile(message.getKnowledgeBaseId()))
                    .flatMapMany(profile -> batches.concatMap(batch ->
                            embeddingBatchProcessor.processChunkBatch(batch, message, profile)));
            })
            .reduce(new EmbeddingBatchResult(false, true), EmbeddingBatchResult::append)
            .flatMap(result -> {
                if (!result.processedAny()) {
                    return recordService.completeProcessing(
                            message.getDocumentId(), message.getProcessingType(), "No pending chunks")
                        .thenReturn(true);
                }
                if (!result.success()) {
                    return Mono.just(false);
                }
                return updateDocumentStatus(message.getDocumentId(), null,
                        DocumentProcessingConstants.Status.COMPLETED, null)
                    .then(embeddingBatchProcessor.completeDocumentEmbedding(message.getDocumentId()))
                    .then(recordService.completeProcessing(
                            message.getDocumentId(), message.getProcessingType(), "Embedding completed"))
                    .thenReturn(true);
            })
            .doOnSuccess(result -> log.info("文档向量化处理完成: documentId={}, result={}", 
                    message.getDocumentId(), result))
            .doOnError(error -> log.error("文档向量化处理失败: documentId={}, error={}",
                    message.getDocumentId(), error.getMessage(), error));
    }
    
    @Override
    public Mono<Boolean> reparseDocument(Long documentId, String parseMode) {
        return documentRepository.findById(documentId)
            .flatMap(document -> {
                log.info("重新解析文档: documentId={}, parseMode={}", documentId, parseMode);
                
                // 重置解析状态
                document.setParseStatus(DocumentProcessingConstants.Status.PENDING);
                document.setContent(null);
                document.setCharacterCount(0);
                document.setUpdatedAt(LocalDateTime.now());
                
                return documentRepository.save(document)
                    .then(knowledgeChunkSearchService.deleteDocumentChunks(documentId))
                    .then(documentChunkService.deleteDocumentChunks(documentId))
                    .then(embeddingService.deleteDocumentEmbeddings(documentId))
                    .then(startDocumentProcessing(documentId, document.getKnowledgeBaseId(), 
                            parseMode, true));
            })
            .doOnSuccess(result -> log.info("重新解析文档完成: documentId={}, result={}", documentId, result))
            .onErrorReturn(false);
    }
    
    @Override
    public Mono<Boolean> reEmbedDocument(Long documentId) {
        return documentRepository.findById(documentId)
            .flatMap(document -> {
                log.info("重新向量化文档: documentId={}", documentId);
                
                // 重置向量化状态
                return embeddingService.deleteDocumentEmbeddings(documentId)
                    .then(embeddingBatchProcessor.resetChunksEmbeddingStatus(documentId, effectiveEmbeddingBatchSize()))
                    .then(updateDocumentStatus(documentId, null, 
                            DocumentProcessingConstants.Status.PENDING, null))
                    .then(knowledgeChunkSearchService.indexDocumentChunks(documentId))
                    .then(Mono.fromCallable(() -> {
                        // 发送向量化消息
                        DocumentProcessingMessage message = DocumentProcessingMessage.createEmbedMessage(
                            documentId, document.getKnowledgeBaseId());
                        
                        return sendProcessingMessage(message, 
                            DocumentProcessingConstants.RoutingKey.DOCUMENT_EMBED);
                    }).flatMap(mono -> mono));
            })
            .doOnSuccess(result -> log.info("重新向量化文档完成: documentId={}, result={}", documentId, result))
            .onErrorReturn(false);
    }
    
    @Override
    public Mono<Boolean> sendProcessingMessage(DocumentProcessingMessage message, String routingKey) {
        return publisher.publish(message, routingKey);
    }
    
    @Override
    public Mono<Boolean> updateDocumentStatus(Long documentId, String parseStatus, 
                                            String embeddingStatus, String errorMessage) {
        return documentRepository.findById(documentId)
            .flatMap(document -> {
                boolean updated = false;
                
                if (parseStatus != null) {
                    document.setParseStatus(parseStatus);
                    updated = true;
                }
                
                if (embeddingStatus != null) {
                    document.setEmbeddingStatus(embeddingStatus);
                    updated = true;
                }
                
                if (updated) {
                    document.setUpdatedAt(LocalDateTime.now());
                    return documentRepository.save(document).map(saved -> true);
                }
                
                return Mono.just(true);
            })
            .defaultIfEmpty(false);
    }
    
    private int effectiveEmbeddingBatchSize() {
        return Math.max(1, embeddingBatchSize);
    }

    private record EmbeddingBatchResult(boolean processedAny, boolean success) {

        private EmbeddingBatchResult append(Boolean batchSucceeded) {
            return new EmbeddingBatchResult(true, success && Boolean.TRUE.equals(batchSucceeded));
        }
    }

}
