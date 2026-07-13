package org.example.ai_agent.service.impl.knowledge;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.document.KnowledgeChunkDocument;
import org.example.ai_agent.dto.knowledge.KnowledgeChunkIndexProjection;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentChunkRepository;
import org.example.ai_agent.observability.KnowledgeSearchTelemetry;
import org.example.ai_agent.service.KnowledgeChunkSearchService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class KnowledgeChunkSearchServiceImpl implements KnowledgeChunkSearchService {

    private static final String KNOWLEDGE_BASE_STATUS_ACTIVE = "ACTIVE";

    private final KnowledgeDocumentChunkRepository chunkRepository;
    private final ReactiveElasticsearchOperations elasticsearchOperations;
    private final KnowledgeSearchTelemetry searchTelemetry;
    private final AtomicBoolean indexChecked = new AtomicBoolean(false);

    public KnowledgeChunkSearchServiceImpl(KnowledgeDocumentChunkRepository chunkRepository,
                                           ReactiveElasticsearchOperations elasticsearchOperations,
                                           KnowledgeSearchTelemetry searchTelemetry) {
        this.chunkRepository = chunkRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.searchTelemetry = searchTelemetry;
    }

    @Override
    public Flux<KnowledgeChunkSearchHit> searchAuthorizedChunks(SearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()
                || request.authorizedKnowledgeBaseIds() == null
                || request.authorizedKnowledgeBaseIds().isEmpty()) {
            return Flux.empty();
        }
        int queryLength = request.query().length();

        return searchTelemetry.observe(ensureIndex()
                .thenMany(elasticsearchOperations.search(buildSearchQuery(request), KnowledgeChunkDocument.class))
                .map(hit -> new KnowledgeChunkSearchHit(hit.getContent(), hit.getScore()))
                .doOnComplete(() -> log.info("知识库 BM25 检索完成: queryLength={}, knowledgeBaseCount={}, maxResults={}",
                        queryLength, request.authorizedKnowledgeBaseIds().size(), request.maxResults()))
                .onErrorResume(error -> {
                    if (isIndexMissing(error)) {
                        log.warn("知识库 BM25 索引不存在，返回空结果: errorType={}",
                                error.getClass().getSimpleName());
                        return Flux.empty();
                    }
                    log.error("知识库 BM25 检索失败: queryLength={}, errorType={}",
                            queryLength, error.getClass().getSimpleName());
                    return Flux.error(error);
                }));
    }

    @Override
    public Mono<Void> indexDocumentChunks(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return Mono.empty();
        }
        return ensureIndex()
                .then(deleteDocumentChunks(documentId))
                .thenMany(chunkRepository.findIndexRowsByDocumentId(documentId)
                        .map(this::toDocument)
                        .buffer(100)
                        .concatMap(this::saveBatch))
                .then()
                .doOnSuccess(unused -> log.info("知识库 BM25 文档块索引刷新完成: documentId={}", documentId));
    }

    @Override
    public Mono<Void> refreshKnowledgeBaseChunks(Long knowledgeBaseId) {
        if (knowledgeBaseId == null || knowledgeBaseId <= 0) {
            return Mono.empty();
        }
        return ensureIndex()
                .then(deleteKnowledgeBaseChunks(knowledgeBaseId))
                .thenMany(chunkRepository.findIndexRowsByKnowledgeBaseId(knowledgeBaseId)
                        .map(this::toDocument)
                        .buffer(100)
                        .concatMap(this::saveBatch))
                .then()
                .doOnSuccess(unused -> log.info("知识库 BM25 索引按知识库刷新完成: knowledgeBaseId={}", knowledgeBaseId));
    }

    @Override
    public Mono<Void> deleteDocumentChunks(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return Mono.empty();
        }
        return ensureIndex()
                .then(elasticsearchOperations.delete(termDeleteQuery("documentId", documentId), KnowledgeChunkDocument.class))
                .doOnSuccess(deleted -> log.info("知识库 BM25 文档索引删除完成: documentId={}, deleted={}", documentId, deleted))
                .then()
                .onErrorResume(error -> isIndexMissing(error) ? Mono.empty() : Mono.error(error));
    }

    @Override
    public Mono<Void> deleteKnowledgeBaseChunks(Long knowledgeBaseId) {
        if (knowledgeBaseId == null || knowledgeBaseId <= 0) {
            return Mono.empty();
        }
        return ensureIndex()
                .then(elasticsearchOperations.delete(termDeleteQuery("knowledgeBaseId", knowledgeBaseId), KnowledgeChunkDocument.class))
                .doOnSuccess(deleted -> log.info("知识库 BM25 知识库索引删除完成: knowledgeBaseId={}, deleted={}",
                        knowledgeBaseId, deleted))
                .then()
                .onErrorResume(error -> isIndexMissing(error) ? Mono.empty() : Mono.error(error));
    }

    @Override
    public Mono<Void> rebuildAllChunks() {
        return ensureIndex()
                .then(elasticsearchOperations.indexOps(KnowledgeChunkDocument.class).delete())
                .then(elasticsearchOperations.indexOps(KnowledgeChunkDocument.class).createWithMapping())
                .thenMany(chunkRepository.findAllIndexRows()
                        .map(this::toDocument)
                        .buffer(100)
                        .concatMap(this::saveBatch))
                .then()
                .doOnSuccess(unused -> log.info("知识库 BM25 索引全量重建完成"));
    }

    private Mono<Void> ensureIndex() {
        if (indexChecked.get()) {
            return Mono.empty();
        }
        return elasticsearchOperations.indexOps(KnowledgeChunkDocument.class)
                .exists()
                .flatMap(exists -> exists
                        ? Mono.just(true)
                        : elasticsearchOperations.indexOps(KnowledgeChunkDocument.class).createWithMapping())
                .doOnSuccess(unused -> indexChecked.set(true))
                .then();
    }

    private Flux<KnowledgeChunkDocument> saveBatch(List<KnowledgeChunkDocument> documents) {
        return Flux.fromIterable(documents)
                .flatMap(elasticsearchOperations::save);
    }

    StringQuery buildSearchQuery(SearchRequest request) {
        JSONObject boolClause = new JSONObject();
        JSONArray mustClauses = new JSONArray();
        JSONArray filterClauses = new JSONArray();

        mustClauses.add(new JSONObject().fluentPut("match",
                new JSONObject().fluentPut("content", request.query())));

        filterClauses.add(new JSONObject().fluentPut("term",
                new JSONObject().fluentPut("knowledgeBaseStatus", KNOWLEDGE_BASE_STATUS_ACTIVE)));
        filterClauses.add(new JSONObject().fluentPut("term",
                new JSONObject().fluentPut("documentParseStatus", DocumentProcessingConstants.Status.COMPLETED)));
        filterClauses.add(new JSONObject().fluentPut("terms",
                new JSONObject().fluentPut("knowledgeBaseId", request.authorizedKnowledgeBaseIds())));

        if (!request.admin()) {
            JSONArray permissionShould = new JSONArray();
            permissionShould.add(new JSONObject().fluentPut("term",
                    new JSONObject().fluentPut("knowledgeBaseIsPublic", true)));
            if (request.requesterUserId() != null && request.requesterUserId() > 0) {
                permissionShould.add(new JSONObject().fluentPut("term",
                        new JSONObject().fluentPut("knowledgeBaseCreatorId", request.requesterUserId())));
                permissionShould.add(new JSONObject().fluentPut("term",
                        new JSONObject().fluentPut("documentUploaderId", request.requesterUserId())));
            }
            filterClauses.add(new JSONObject().fluentPut("bool",
                    new JSONObject()
                            .fluentPut("should", permissionShould)
                            .fluentPut("minimum_should_match", 1)));
        }

        boolClause.put("must", mustClauses);
        boolClause.put("filter", filterClauses);

        StringQuery query = new StringQuery(new JSONObject()
                .fluentPut("bool", boolClause)
                .toJSONString());
        query.setPageable(PageRequest.of(0, Math.max(1, request.maxResults())));
        return query;
    }

    DeleteQuery termDeleteQuery(String field, Object value) {
        StringQuery query = new StringQuery(new JSONObject()
                .fluentPut("term", new JSONObject().fluentPut(field, value))
                .toJSONString());
        return DeleteQuery.builder(query).build();
    }

    private KnowledgeChunkDocument toDocument(KnowledgeChunkIndexProjection row) {
        return new KnowledgeChunkDocument(
                row.getChunkId().toString(),
                row.getChunkId(),
                row.getDocumentId(),
                row.getKnowledgeBaseId(),
                row.getChunkIndex(),
                row.getContent(),
                defaultText(row.getDocumentTitle(), "未知文档"),
                defaultText(row.getKnowledgeBaseName(), "未知知识库"),
                row.getKnowledgeBaseCreatorId(),
                Boolean.TRUE.equals(row.getKnowledgeBaseIsPublic()),
                row.getDocumentUploaderId(),
                row.getKnowledgeBaseStatus(),
                row.getDocumentParseStatus(),
                row.getDocumentEmbeddingStatus(),
                row.getChunkEmbeddingStatus(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean isIndexMissing(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("index_not_found_exception")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
