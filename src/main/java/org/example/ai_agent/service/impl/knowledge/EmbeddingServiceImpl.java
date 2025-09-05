package org.example.ai_agent.service.impl.knowledge;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.ai.AiModelProperties;
import org.example.ai_agent.service.EmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * 向量化服务实现类
 * <p>
 * 基于LangChain4j 1.0.1的EmbeddingModel和EmbeddingStore实现
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
@Slf4j
@Service
public class EmbeddingServiceImpl implements EmbeddingService {
    
    @Autowired
    private AiModelProperties aiModelProperties;
    
    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;
    
    @Value("${knowledge-base.embedding.default-model:qwen-embedding-8b}")
    private String defaultEmbeddingModel;
    
    // 缓存不同模型的EmbeddingModel实例
    private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();
    
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
                log.error("文本向量化失败: modelId={}, error={}", modelId, e.getMessage(), e);
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
                log.error("批量文本向量化失败: modelId={}, textCount={}, error={}", 
                         modelId, textSegments.size(), e.getMessage(), e);
                throw new RuntimeException("批量文本向量化失败: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<String> storeEmbedding(Long knowledgeBaseId, Long documentId, Long chunkId, 
                                     Embedding embedding, TextSegment textSegment) {
        return Mono.fromCallable(() -> {
            try {
                // 添加元数据
                TextSegment enhancedSegment = TextSegment.from(
                    textSegment.text(),
                    textSegment.metadata().copy()
                        .put("knowledge_base_id", knowledgeBaseId.toString())
                        .put("document_id", documentId.toString())
                        .put("chunk_id", chunkId.toString())
                        .put("stored_time", System.currentTimeMillis())
                );
                
                String embeddingId = embeddingStore.add(embedding, enhancedSegment);
                
                log.debug("向量存储成功: knowledgeBaseId={}, documentId={}, chunkId={}, embeddingId={}", 
                         knowledgeBaseId, documentId, chunkId, embeddingId);
                
                return embeddingId;
                
            } catch (Exception e) {
                log.error("向量存储失败: knowledgeBaseId={}, documentId={}, chunkId={}, error={}", 
                         knowledgeBaseId, documentId, chunkId, e.getMessage(), e);
                throw new RuntimeException("向量存储失败: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<List<String>> storeEmbeddings(Long knowledgeBaseId, Long documentId, List<Long> chunkIds,
                                            List<Embedding> embeddings, List<TextSegment> textSegments) {
        return Mono.fromCallable(() -> {
            try {
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
                                .put("stored_time", System.currentTimeMillis())
                        );
                    })
                    .toList();
                
                List<String> embeddingIds = embeddingStore.addAll(embeddings, enhancedSegments);
                
                log.info("批量向量存储成功: knowledgeBaseId={}, documentId={}, count={}", 
                        knowledgeBaseId, documentId, embeddingIds.size());
                
                return embeddingIds;
                
            } catch (Exception e) {
                log.error("批量向量存储失败: knowledgeBaseId={}, documentId={}, error={}", 
                         knowledgeBaseId, documentId, e.getMessage(), e);
                throw new RuntimeException("批量向量存储失败: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<List<EmbeddingService.EmbeddingSearchResult>> searchSimilarEmbeddings(
            Embedding queryEmbedding, List<Long> knowledgeBaseIds, Integer maxResults, Double minScore) {
        
        return Mono.fromCallable(() -> {
            try {
                var requestBuilder = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults != null ? maxResults : 10)
                    .minScore(minScore != null ? minScore : 0.7);
                
                // 添加知识库过滤条件
                if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                    List<Filter> filters = knowledgeBaseIds.stream()
                        .map(id -> new IsEqualTo("knowledge_base_id", id.toString()))
                        .map(filter -> (Filter) filter)
                        .toList();
                    
                    Filter combinedFilter = filters.size() == 1 ? 
                        filters.get(0) : 
                        new And(filters.get(0), filters.get(1)); // 简化版本，实际应该支持更多过滤条件
                    
                    requestBuilder.filter(combinedFilter);
                }
                
                dev.langchain4j.store.embedding.EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(requestBuilder.build());
                
                List<EmbeddingService.EmbeddingSearchResult> results = searchResult.matches().stream()
                    .map(this::convertToServiceResult)
                    .toList();
                
                log.info("向量搜索完成: queryDimension={}, knowledgeBaseIds={}, resultCount={}", 
                        queryEmbedding.dimension(), knowledgeBaseIds, results.size());
                
                return results;
                
            } catch (Exception e) {
                log.error("向量搜索失败: knowledgeBaseIds={}, error={}", knowledgeBaseIds, e.getMessage(), e);
                throw new RuntimeException("向量搜索失败: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Boolean> deleteDocumentEmbeddings(Long documentId) {
        return Mono.fromCallable(() -> {
            try {
                Filter filter = new IsEqualTo("document_id", documentId.toString());
                embeddingStore.removeAll(filter);
                
                log.info("文档向量删除成功: documentId={}", documentId);
                return true;
                
            } catch (Exception e) {
                log.error("文档向量删除失败: documentId={}, error={}", documentId, e.getMessage(), e);
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Boolean> deleteKnowledgeBaseEmbeddings(Long knowledgeBaseId) {
        return Mono.fromCallable(() -> {
            try {
                Filter filter = new IsEqualTo("knowledge_base_id", knowledgeBaseId.toString());
                embeddingStore.removeAll(filter);
                
                log.info("知识库向量删除成功: knowledgeBaseId={}", knowledgeBaseId);
                return true;
                
            } catch (Exception e) {
                log.error("知识库向量删除失败: knowledgeBaseId={}, error={}", knowledgeBaseId, e.getMessage(), e);
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 获取向量化模型实例
     */
    private EmbeddingModel getEmbeddingModel(String modelId) {
        String actualModelId = modelId != null ? modelId : defaultEmbeddingModel;
        
        return embeddingModelCache.computeIfAbsent(actualModelId, id -> {
            String globalApiKey = aiModelProperties.getApiKey();
            String globalBaseUrl = aiModelProperties.getBaseUrl();
            
            log.info("创建向量化模型实例: modelId={}", actualModelId);
            
            // 使用硅基流动的Qwen嵌入模型
            return OpenAiEmbeddingModel.builder()
                    .apiKey(globalApiKey)
                    .baseUrl(globalBaseUrl)
                    .modelName("Qwen/Qwen3-Embedding-8B")
                    .build();
        });
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
}