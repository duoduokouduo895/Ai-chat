package org.example.ai_agent.service.impl.document;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.entity.KnowledgeDocument;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository;
import org.example.ai_agent.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
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
    private final DocumentProcessingStages stages;
    private final DocumentEmbeddingBatchProcessor embeddingBatchProcessor;
    private final DocumentProcessingOutboxService outboxService;
    private final TransactionalOperator transactionalOperator;
    private final int embeddingBatchSize;

    public DocumentProcessingServiceImpl(KnowledgeDocumentRepository documentRepository,
                                         DocumentChunkService documentChunkService,
                                         EmbeddingService embeddingService,
                                         DocumentProcessingRecordService recordService,
                                         KnowledgeChunkSearchService knowledgeChunkSearchService,
                                         DocumentProcessingStages stages,
                                         DocumentEmbeddingBatchProcessor embeddingBatchProcessor,
                                         DocumentProcessingOutboxService outboxService,
                                         TransactionalOperator transactionalOperator,
                                         @Value("${knowledge-base.embedding.batch-size:32}") int embeddingBatchSize) {
        this.documentRepository = documentRepository;
        this.documentChunkService = documentChunkService;
        this.embeddingService = embeddingService;
        this.recordService = recordService;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
        this.stages = stages;
        this.embeddingBatchProcessor = embeddingBatchProcessor;
        this.outboxService = outboxService;
        this.transactionalOperator = transactionalOperator;
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
                    .flatMap(parsedContent -> persistParsedContentAndQueueNextStage(document, message, parsedContent));
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
                    .flatMap(chunkCount -> persistChunkResultAndQueueNextStage(document, message, chunkCount));
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
                
                return knowledgeChunkSearchService.deleteDocumentChunks(documentId)
                    .then(documentChunkService.deleteDocumentChunks(documentId))
                    .then(embeddingService.deleteDocumentEmbeddings(documentId))
                    .then(saveReparseStateAndQueue(document, parseMode));
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
                    .then(knowledgeChunkSearchService.indexDocumentChunks(documentId))
                    .then(saveReEmbedStateAndQueue(document));
            })
            .doOnSuccess(result -> log.info("重新向量化文档完成: documentId={}, result={}", documentId, result))
            .onErrorReturn(false);
    }
    
    @Override
    public Mono<Boolean> sendProcessingMessage(DocumentProcessingMessage message, String routingKey) {
        return outboxService.enqueue(message, routingKey);
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

    /**
     * 把解析结果、下一阶段 outbox 事件以及本阶段完成记录放在同一个本地事务中。
     * 即使进程在事务提交后宕机，outbox 投递器也会继续补发下一阶段消息。
     */
    private Mono<Boolean> persistParsedContentAndQueueNextStage(KnowledgeDocument document,
                                                                 DocumentProcessingMessage message,
                                                                 String parsedContent) {
        document.setContent(parsedContent);
        document.setCharacterCount(parsedContent.length());
        document.setParseStatus(DocumentProcessingConstants.Status.COMPLETED);
        document.setUpdatedAt(LocalDateTime.now());

        DocumentProcessingMessage chunkMessage = DocumentProcessingMessage.createChunkMessage(
                message.getDocumentId(), message.getKnowledgeBaseId(), message.getEnableVectorization());

        return documentRepository.save(document)
                .flatMap(savedDocument -> sendProcessingMessage(
                                chunkMessage, DocumentProcessingConstants.RoutingKey.DOCUMENT_CHUNK)
                        .flatMap(queued -> Boolean.TRUE.equals(queued)
                                ? recordService.completeProcessing(
                                                message.getDocumentId(),
                                                message.getProcessingType(),
                                                "Content length: " + savedDocument.getContent().length())
                                        .thenReturn(true)
                                : Mono.just(false)))
                .as(transactionalOperator::transactional);
    }

    /** 把分块数量、可选的向量化 outbox 事件和分块完成记录原子提交。 */
    private Mono<Boolean> persistChunkResultAndQueueNextStage(KnowledgeDocument document,
                                                               DocumentProcessingMessage message,
                                                               Integer chunkCount) {
        document.setChunkCount(chunkCount);
        document.setUpdatedAt(LocalDateTime.now());

        return documentRepository.save(document)
                .then(Boolean.TRUE.equals(message.getEnableVectorization())
                        ? queueEmbeddingAndCompleteChunking(message, chunkCount)
                        : recordService.completeProcessing(
                                        message.getDocumentId(),
                                        message.getProcessingType(),
                                        "Chunks created: " + chunkCount)
                                .thenReturn(true))
                .as(transactionalOperator::transactional);
    }

    private Mono<Boolean> queueEmbeddingAndCompleteChunking(DocumentProcessingMessage message, Integer chunkCount) {
        DocumentProcessingMessage embedMessage = DocumentProcessingMessage.createEmbedMessage(
                message.getDocumentId(), message.getKnowledgeBaseId());
        return sendProcessingMessage(embedMessage, DocumentProcessingConstants.RoutingKey.DOCUMENT_EMBED)
                .flatMap(queued -> Boolean.TRUE.equals(queued)
                        ? recordService.completeProcessing(
                                        message.getDocumentId(),
                                        message.getProcessingType(),
                                        "Chunks created: " + chunkCount)
                                .thenReturn(true)
                        : Mono.just(false));
    }

    private Mono<Boolean> saveReparseStateAndQueue(KnowledgeDocument document, String parseMode) {
        return documentRepository.save(document)
                .then(startDocumentProcessing(document.getId(), document.getKnowledgeBaseId(), parseMode, true))
                .as(transactionalOperator::transactional);
    }

    private Mono<Boolean> saveReEmbedStateAndQueue(KnowledgeDocument document) {
        document.setEmbeddingStatus(DocumentProcessingConstants.Status.PENDING);
        document.setIsEmbedded(false);
        document.setUpdatedAt(LocalDateTime.now());
        DocumentProcessingMessage message = DocumentProcessingMessage.createEmbedMessage(
                document.getId(), document.getKnowledgeBaseId());
        return documentRepository.save(document)
                .then(sendProcessingMessage(message, DocumentProcessingConstants.RoutingKey.DOCUMENT_EMBED))
                .as(transactionalOperator::transactional);
    }

    private record EmbeddingBatchResult(boolean processedAny, boolean success) {

        private EmbeddingBatchResult append(Boolean batchSucceeded) {
            return new EmbeddingBatchResult(true, success && Boolean.TRUE.equals(batchSucceeded));
        }
    }

}
