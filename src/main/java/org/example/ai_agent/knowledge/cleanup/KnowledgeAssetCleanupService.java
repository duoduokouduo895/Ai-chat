package org.example.ai_agent.knowledge.cleanup;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.knowledge.entity.KnowledgeBase;
import org.example.ai_agent.knowledge.entity.KnowledgeDocument;
import org.example.ai_agent.document.repository.DocumentProcessingRecordRepository;
import org.example.ai_agent.knowledge.repository.KnowledgeDocumentChunkRepository;
import org.example.ai_agent.knowledge.repository.KnowledgeDocumentRepository;
import org.example.ai_agent.knowledge.service.EmbeddingService;
import org.example.ai_agent.knowledge.service.KnowledgeChunkSearchService;
import org.example.ai_agent.common.storage.OssService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 统一负责知识库/文档级联清理，避免删除主记录后残留 OSS、分块、向量和处理记录。
 */
@Slf4j
@Service
public class KnowledgeAssetCleanupService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentChunkRepository chunkRepository;
    private final DocumentProcessingRecordRepository recordRepository;
    private final EmbeddingService embeddingService;
    private final KnowledgeChunkSearchService knowledgeChunkSearchService;
    private final OssService ossService;

    public KnowledgeAssetCleanupService(KnowledgeDocumentRepository documentRepository,
                                        KnowledgeDocumentChunkRepository chunkRepository,
                                        DocumentProcessingRecordRepository recordRepository,
                                        EmbeddingService embeddingService,
                                        KnowledgeChunkSearchService knowledgeChunkSearchService,
                                        OssService ossService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.recordRepository = recordRepository;
        this.embeddingService = embeddingService;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
        this.ossService = ossService;
    }

    public Mono<Void> deleteDocumentAggregate(KnowledgeDocument document) {
        if (document == null || document.getId() == null) {
            return Mono.error(new IllegalArgumentException("文档不存在，无法清理关联资源"));
        }

        Long documentId = document.getId();
        return Mono.whenDelayError(
                        deleteDocumentEmbeddings(documentId),
                        knowledgeChunkSearchService.deleteDocumentChunks(documentId),
                        deleteOssFile(document),
                        chunkRepository.deleteByDocumentId(documentId).then(),
                        recordRepository.deleteByDocumentId(documentId).then()
                )
                .then(documentRepository.deleteById(documentId))
                .doOnSuccess(unused -> log.info("文档级联删除完成: documentId={}", documentId))
                .doOnError(error -> log.error("文档级联删除失败: documentId={}, errorType={}",
                        documentId, error.getClass().getSimpleName()));
    }

    public Mono<Void> deleteKnowledgeBaseAssets(KnowledgeBase knowledgeBase) {
        if (knowledgeBase == null || knowledgeBase.getId() == null) {
            return Mono.error(new IllegalArgumentException("知识库不存在，无法清理关联资源"));
        }

        Long knowledgeBaseId = knowledgeBase.getId();
        return documentRepository.findByKnowledgeBaseId(knowledgeBaseId)
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(this::deleteDocumentAggregate)
                .then(knowledgeChunkSearchService.deleteKnowledgeBaseChunks(knowledgeBaseId))
                .then(deleteKnowledgeBaseEmbeddings(knowledgeBaseId))
                .doOnSuccess(unused -> log.info("知识库关联资源清理完成: knowledgeBaseId={}", knowledgeBaseId))
                .doOnError(error -> log.error("知识库关联资源清理失败: knowledgeBaseId={}, errorType={}",
                        knowledgeBaseId, error.getClass().getSimpleName()));
    }

    private Mono<Void> deleteDocumentEmbeddings(Long documentId) {
        return embeddingService.deleteDocumentEmbeddings(documentId)
                .flatMap(success -> success
                        ? Mono.<Void>empty()
                        : Mono.error(new RuntimeException("删除文档向量失败: documentId=" + documentId)));
    }

    private Mono<Void> deleteKnowledgeBaseEmbeddings(Long knowledgeBaseId) {
        return embeddingService.deleteKnowledgeBaseEmbeddings(knowledgeBaseId)
                .flatMap(success -> success
                        ? Mono.<Void>empty()
                        : Mono.error(new RuntimeException("删除知识库向量失败: knowledgeBaseId=" + knowledgeBaseId)));
    }

    private Mono<Void> deleteOssFile(KnowledgeDocument document) {
        if (!StringUtils.hasText(document.getOssPath())) {
            return Mono.empty();
        }

        return ossService.deleteFile(document.getOssPath())
                .flatMap(success -> success
                        ? Mono.<Void>empty()
                        : Mono.error(new RuntimeException("删除 OSS 文件失败: ossPath=" + document.getOssPath())));
    }
}
