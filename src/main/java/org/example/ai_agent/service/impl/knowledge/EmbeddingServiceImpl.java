package org.example.ai_agent.service.impl.knowledge;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.Or;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.service.EmbeddingService;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.example.ai_agent.service.registry.EmbeddingStoreRegistry;
import org.example.ai_agent.service.registry.EmbeddingStoreRegistry.EmbeddingProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 向量化服务实现类
 * <p>
 * 基于LangChain4j 1.0.1的EmbeddingModel和EmbeddingStore实现
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {
    
    @Autowired
    private AiModelRegistry aiModelRegistry;
    
    @Autowired
    private EmbeddingStoreRegistry embeddingStoreRegistry;
    
    @Override
    public Mono<Embedding> embedText(TextSegment textSegment, String modelId) {
        return Mono.fromCallable(() -> {
            try {
                EmbeddingModel embeddingModel = getEmbeddingModel(modelId);
                Response<Embedding> response = embeddingModel.embed(textSegment);
                
                log.debug("文本向量化完成: modelId={}, textLength={}, dimension={}", 
                         modelId, textSegment.text().length(), response.content().dimension());
                
                return response.content();
                
            } catch (Exception e) {
                log.error("文本向量化失败: modelId={}, errorType={}", modelId, e.getClass().getSimpleName());
                throw new RuntimeException("文本向量化失败: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<List<Embedding>> embedTexts(List<TextSegment> textSegments, String modelId) {
        return Mono.fromCallable(() -> {
            try {
                EmbeddingModel embeddingModel = getEmbeddingModel(modelId);
                Response<List<Embedding>> response = embeddingModel.embedAll(textSegments);
                
                log.info("批量文本向量化完成: modelId={}, textCount={}, dimension={}", 
                        modelId, textSegments.size(), 
                        response.content().isEmpty() ? 0 : response.content().get(0).dimension());
                
                return response.content();
                
            } catch (Exception e) {
                log.error("批量文本向量化失败: modelId={}, textCount={}, errorType={}",
                        modelId, textSegments.size(), e.getClass().getSimpleName());
                throw new RuntimeException("批量文本向量化失败: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<String> storeEmbedding(Long knowledgeBaseId, Long documentId, Long chunkId, 
                                     Embedding embedding, TextSegment textSegment,
                                     String modelId, String indexName) {
        return Mono.fromCallable(() -> {
            try {
                EmbeddingProfile profile = embeddingStoreRegistry.requireProfile(modelId, indexName);
                EmbeddingStore<TextSegment> embeddingStore = embeddingStoreRegistry.getWritableStore(profile);
                // 添加元数据
                TextSegment enhancedSegment = TextSegment.from(
                    textSegment.text(),
                    textSegment.metadata().copy()
                        .put("knowledge_base_id", knowledgeBaseId.toString())
                        .put("document_id", documentId.toString())
                        .put("chunk_id", chunkId.toString())
                        .put("embedding_model", profile.modelAlias())
                        .put("embedding_version", profile.version())
                        .put("stored_time", System.currentTimeMillis())
                );
                
                String embeddingId = embeddingStore.add(embedding, enhancedSegment);
                
                log.debug("向量存储成功: knowledgeBaseId={}, documentId={}, chunkId={}, embeddingId={}", 
                         knowledgeBaseId, documentId, chunkId, embeddingId);
                
                return embeddingId;
                
            } catch (Exception e) {
                log.error("向量存储失败: knowledgeBaseId={}, documentId={}, chunkId={}, errorType={}",
                        knowledgeBaseId, documentId, chunkId, e.getClass().getSimpleName());
                throw new RuntimeException("向量存储失败: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<List<String>> storeEmbeddings(Long knowledgeBaseId, Long documentId, List<Long> chunkIds,
                                            List<Embedding> embeddings, List<TextSegment> textSegments,
                                            String modelId, String indexName) {
        return Mono.fromCallable(() -> {
            try {
                EmbeddingProfile profile = embeddingStoreRegistry.requireProfile(modelId, indexName);
                EmbeddingStore<TextSegment> embeddingStore = embeddingStoreRegistry.getWritableStore(profile);
                // 增强文本段，添加元数据
                List<TextSegment> enhancedSegments = IntStream.range(0, textSegments.size())
                    .mapToObj(i -> {
                        TextSegment original = textSegments.get(i);
                        Long chunkId = chunkIds.get(i);
                        return TextSegment.from(
                            original.text(),
                            original.metadata().copy()
                                .put("knowledge_base_id", knowledgeBaseId.toString())
                                .put("document_id", documentId.toString())
                                .put("chunk_id", chunkId.toString())
                                .put("embedding_model", profile.modelAlias())
                                .put("embedding_version", profile.version())
                                .put("stored_time", System.currentTimeMillis())
                        );
                    })
                    .toList();
                
                List<String> embeddingIds = embeddingStore.addAll(embeddings, enhancedSegments);
                
                log.info("批量向量存储成功: knowledgeBaseId={}, documentId={}, count={}", 
                        knowledgeBaseId, documentId, embeddingIds.size());
                
                return embeddingIds;
                
            } catch (Exception e) {
                log.error("批量向量存储失败: knowledgeBaseId={}, documentId={}, errorType={}",
                        knowledgeBaseId, documentId, e.getClass().getSimpleName());
                throw new RuntimeException("批量向量存储失败: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<List<EmbeddingService.EmbeddingSearchResult>> searchSimilarEmbeddings(
            Embedding queryEmbedding, List<Long> knowledgeBaseIds, Integer maxResults, Double minScore,
            String modelId, String indexName) {
        
        return Mono.fromCallable(() -> {
            try {
                EmbeddingProfile profile = embeddingStoreRegistry.requireProfile(modelId, indexName);
                EmbeddingStore<TextSegment> embeddingStore = embeddingStoreRegistry.getStore(profile);
                var requestBuilder = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults != null ? maxResults : 10)
                    .minScore(minScore != null ? minScore : 0.7);
                
                // 添加知识库过滤条件
                if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                    List<Filter> filters = knowledgeBaseIds.stream()
                        .map(id -> (Filter) new IsEqualTo("knowledge_base_id", id.toString()))
                        .toList();
                    
                    Filter combinedFilter = filters.stream()
                        .reduce((left, right) -> new Or(left, right))
                        .orElse(null);
                    
                    if (combinedFilter != null) {
                        requestBuilder.filter(combinedFilter);
                    }
                }
                
                dev.langchain4j.store.embedding.EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(requestBuilder.build());
                
                List<EmbeddingService.EmbeddingSearchResult> results = searchResult.matches().stream()
                    .map(this::convertToServiceResult)
                    .toList();
                
                log.info("向量搜索完成: queryDimension={}, knowledgeBaseCount={}, resultCount={}",
                        queryEmbedding.dimension(), knowledgeBaseIds.size(), results.size());
                
                return results;
                
            } catch (Exception e) {
                if (isIndexNotFound(e)) {
                    log.warn("向量索引不存在，返回空检索结果: knowledgeBaseCount={}, errorType={}",
                            knowledgeBaseIds.size(), e.getClass().getSimpleName());
                    return List.<EmbeddingService.EmbeddingSearchResult>of();
                }
                log.error("向量搜索失败: knowledgeBaseCount={}, errorType={}",
                        knowledgeBaseIds.size(), e.getClass().getSimpleName());
                throw new RuntimeException("向量搜索失败: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Boolean> deleteDocumentEmbeddings(Long documentId) {
        return Mono.fromCallable(() -> {
            boolean success = true;
            for (EmbeddingProfile profile : embeddingStoreRegistry.getCleanupProfiles()) {
                success &= removeAll(profile, new IsEqualTo("document_id", documentId.toString()),
                        "文档", documentId);
            }
            return success;
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Boolean> deleteKnowledgeBaseEmbeddings(Long knowledgeBaseId) {
        return Mono.fromCallable(() -> {
            boolean success = true;
            for (EmbeddingProfile profile : embeddingStoreRegistry.getCleanupProfiles()) {
                success &= removeAll(profile, new IsEqualTo("knowledge_base_id", knowledgeBaseId.toString()),
                        "知识库", knowledgeBaseId);
            }
            return success;
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 获取向量化模型实例
     */
    private EmbeddingModel getEmbeddingModel(String modelId) {
        String actualModelId = modelId != null ? modelId : aiModelRegistry.getDefaultEmbeddingModel();
        return aiModelRegistry.getEmbeddingModel(actualModelId);
    }

    private boolean removeAll(EmbeddingProfile profile, Filter filter, String resourceType, Long resourceId) {
        try {
            embeddingStoreRegistry.getStore(profile).removeAll(filter);
            log.info("{}向量删除成功: resourceId={}, indexName={}", resourceType, resourceId, profile.indexName());
            return true;
        } catch (Exception e) {
            if (isIndexNotFound(e)) {
                log.debug("向量索引不存在，清理视为完成: indexName={}", profile.indexName());
                return true;
            }
            log.error("{}向量删除失败: resourceId={}, indexName={}, errorType={}",
                    resourceType, resourceId, profile.indexName(), e.getClass().getSimpleName());
            return false;
        }
    }
    
    /**
     * 转换搜索结果格式
     */
    private EmbeddingService.EmbeddingSearchResult convertToServiceResult(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        var metadata = segment.metadata();
        
        String knowledgeBaseId = metadata.getString("knowledge_base_id");
        String documentId = metadata.getString("document_id");
        String chunkId = metadata.getString("chunk_id");
        
        return new EmbeddingService.EmbeddingSearchResult(
            match.embeddingId(),
            knowledgeBaseId != null ? Long.parseLong(knowledgeBaseId) : 0L,
            documentId != null ? Long.parseLong(documentId) : 0L,
            chunkId != null ? Long.parseLong(chunkId) : 0L,
            segment,
            match.score()
        );
    }

    private boolean isIndexNotFound(Throwable throwable) {
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

