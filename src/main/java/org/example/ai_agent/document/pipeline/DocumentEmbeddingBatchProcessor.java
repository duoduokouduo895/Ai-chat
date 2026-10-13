package org.example.ai_agent.document.pipeline;

import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.document.domain.DocumentProcessingConstants;
import org.example.ai_agent.document.domain.DocumentProcessingStatus;
import org.example.ai_agent.document.dto.DocumentChunkDto;
import org.example.ai_agent.document.dto.DocumentProcessingMessage;
import org.example.ai_agent.knowledge.repository.KnowledgeBaseRepository;
import org.example.ai_agent.knowledge.repository.KnowledgeDocumentRepository;
import org.example.ai_agent.document.service.DocumentChunkService;
import org.example.ai_agent.knowledge.service.EmbeddingService;
import org.example.ai_agent.knowledge.service.KnowledgeChunkSearchService;
import org.example.ai_agent.knowledge.service.EmbeddingStoreRegistry;
import org.example.ai_agent.knowledge.service.EmbeddingStoreRegistry.EmbeddingProfile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DocumentEmbeddingBatchProcessor {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentChunkService documentChunkService;
    private final EmbeddingService embeddingService;
    private final EmbeddingStoreRegistry embeddingStoreRegistry;
    private final KnowledgeChunkSearchService knowledgeChunkSearchService;

    public DocumentEmbeddingBatchProcessor(KnowledgeDocumentRepository documentRepository,
                                           KnowledgeBaseRepository knowledgeBaseRepository,
                                           DocumentChunkService documentChunkService,
                                           EmbeddingService embeddingService,
                                           EmbeddingStoreRegistry embeddingStoreRegistry,
                                           KnowledgeChunkSearchService knowledgeChunkSearchService) {
        this.documentRepository = documentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentChunkService = documentChunkService;
        this.embeddingService = embeddingService;
        this.embeddingStoreRegistry = embeddingStoreRegistry;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
    }

    public Mono<EmbeddingProfile> resolveEmbeddingProfile(Long knowledgeBaseId) {
        return knowledgeBaseRepository.findById(knowledgeBaseId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("知识库不存在: id=" + knowledgeBaseId)))
                .map(embeddingStoreRegistry::resolveProfile);
    }

    public Mono<Void> refreshEmbeddingIndex(Long knowledgeBaseId) {
        return resolveEmbeddingProfile(knowledgeBaseId).flatMap(profile -> Mono.fromCallable(() -> {
            embeddingStoreRegistry.refresh(profile);
            return true;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())).then();
    }

    public Mono<Boolean> processChunkBatch(List<DocumentChunkDto> chunks,
                                           DocumentProcessingMessage message,
                                           EmbeddingProfile profile) {
        List<Long> chunkIds = chunks.stream()
                .map(DocumentChunkDto::getId)
                .toList();

        log.debug("处理文档向量化批次: documentId={}, batchChunkCount={}",
                message.getDocumentId(), chunks.size());

        return Mono.zip(
                        documentRepository.findById(message.getDocumentId()),
                        knowledgeBaseRepository.findById(message.getKnowledgeBaseId()))
                .map(tuple -> chunks.stream()
                        .map(chunk -> TextSegment.from(chunk.getContent(), chunkMetadata(
                                tuple.getT1().getTitle(),
                                tuple.getT2().getName(),
                                chunk.getChunkIndex())))
                        .toList())
                .flatMap(textSegments -> embeddingService.embedTexts(textSegments, profile.modelAlias())
                        .flatMap(embeddings -> embeddingService.storeEmbeddings(
                                message.getKnowledgeBaseId(),
                                message.getDocumentId(),
                                chunkIds,
                                embeddings,
                                textSegments,
                                profile.modelAlias(),
                                profile.indexName()))
                        .flatMap(embeddingIds -> documentChunkService.batchUpdateChunkEmbeddingStatus(
                                chunkIds, embeddingIds, DocumentProcessingStatus.COMPLETED.value())));
    }

    public Mono<Boolean> completeDocumentEmbedding(Long documentId) {
        return knowledgeChunkSearchService.indexDocumentChunks(documentId)
                .then(Mono.defer(() -> documentRepository.findById(documentId)))
                .flatMap(document -> {
                    document.setIsEmbedded(true);
                    document.setUpdatedAt(LocalDateTime.now());
                    return documentRepository.save(document).map(saved -> true);
                })
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> resetChunksEmbeddingStatus(Long documentId, int batchSize) {
        return documentChunkService.getDocumentChunks(documentId)
                .map(DocumentChunkDto::getId)
                .buffer(Math.max(1, batchSize))
                .concatMap(chunkIds -> documentChunkService.batchUpdateChunkEmbeddingStatus(
                        chunkIds,
                        chunkIds.stream().map(id -> (String) null).toList(),
                        DocumentProcessingStatus.PENDING.value()))
                .all(Boolean.TRUE::equals);
    }

    private dev.langchain4j.data.document.Metadata chunkMetadata(String documentTitle,
                                                                  String knowledgeBaseName,
                                                                  Integer chunkIndex) {
        Map<String, Object> values = new HashMap<>();
        values.put("document_title", documentTitle != null ? documentTitle : "未知文档");
        values.put("knowledge_base_name", knowledgeBaseName != null ? knowledgeBaseName : "未知知识库");
        values.put("chunk_index", chunkIndex != null ? chunkIndex : 0);
        return dev.langchain4j.data.document.Metadata.from(values);
    }
}
