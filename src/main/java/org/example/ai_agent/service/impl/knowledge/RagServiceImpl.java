package org.example.ai_agent.service.impl.knowledge;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchDto;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchResultDto;
import org.example.ai_agent.dto.chat.RagChatDto;
import org.example.ai_agent.dto.chat.RagChatResponseDto;
import org.example.ai_agent.entity.KnowledgeBase;
import org.example.ai_agent.repository.knowledge.KnowledgeBaseRepository;
import org.example.ai_agent.service.EmbeddingService;
import org.example.ai_agent.service.RagService;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.example.ai_agent.service.registry.EmbeddingStoreRegistry;
import org.example.ai_agent.service.registry.EmbeddingStoreRegistry.EmbeddingProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * RAG检索增强生成服务实现类
 * <p>
 * 基于LangChain4j 1.0.1的RAG架构实现
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Service
public class RagServiceImpl implements RagService {
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private AiModelRegistry aiModelRegistry;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private EmbeddingStoreRegistry embeddingStoreRegistry;
    
    @Value("${knowledge-base.rag.max-results:10}")
    private Integer defaultMaxResults;
    
    @Value("${knowledge-base.rag.min-score:0.7}")
    private Double defaultMinScore;
    
    @Override
    public Mono<KnowledgeSearchResultDto> searchKnowledge(KnowledgeSearchDto searchRequest,
                                                          Long requesterUserId, boolean isAdmin) {
        long startTime = System.currentTimeMillis();
        
        return searchAcrossProfiles(
                searchRequest.getQuery(),
                searchRequest.getKnowledgeBaseIds(),
                searchRequest.getMaxResults() != null ? searchRequest.getMaxResults() : defaultMaxResults,
                searchRequest.getMinScore() != null ? searchRequest.getMinScore() : defaultMinScore,
                requesterUserId,
                isAdmin
            )
        .map(searchResults -> {
            // 转换为响应格式
            List<KnowledgeSearchResultDto.KnowledgeSearchMatch> matches = 
                searchResults.stream()
                    .map(this::convertToSearchMatch)
                    .collect(Collectors.toList());
            
            KnowledgeSearchResultDto result = new KnowledgeSearchResultDto();
            result.setMatches(matches);
            result.setTotalCount(matches.size());
            result.setSearchTime(System.currentTimeMillis() - startTime);
            
            log.info("知识库搜索完成: query={}, resultCount={}, searchTime={}ms", 
                    searchRequest.getQuery(), matches.size(), result.getSearchTime());
            
            return result;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<RagChatResponseDto> ragChat(RagChatDto ragRequest, Long requesterUserId, boolean isAdmin) {
        long startTime = System.currentTimeMillis();
        
        return retrieveContext(
                ragRequest.getQuestion(), 
                ragRequest.getKnowledgeBaseIds(),
                ragRequest.getMaxRetrievedDocs() != null ? ragRequest.getMaxRetrievedDocs() : defaultMaxResults,
                ragRequest.getMinSimilarity() != null ? ragRequest.getMinSimilarity() : defaultMinScore,
                requesterUserId,
                isAdmin
        )
        .flatMap(context -> {
            // 构建增强提示词
            String enhancedPrompt = buildRagPrompt(ragRequest.getQuestion(), context.contextText());
            
            // 获取AI模型
            StreamingChatModel chatModel = aiModelRegistry.getStreamingModel(ragRequest.getModelId());
            if (chatModel == null) {
                return Mono.error(new IllegalArgumentException("未找到指定的AI模型: " + ragRequest.getModelId()));
            }
            
            // 执行AI生成
            return generateResponse(chatModel, enhancedPrompt)
                .map(answer -> {
                    RagChatResponseDto response = new RagChatResponseDto();
                    response.setAnswer(answer);
                    response.setSources(context.relevantDocuments());
                    response.setConversationId(ragRequest.getConversationId());
                    response.setResponseTime(LocalDateTime.now());
                    response.setProcessingTime(System.currentTimeMillis() - startTime);
                    
                    log.info("RAG对话完成: question={}, sourceCount={}, processingTime={}ms", 
                            ragRequest.getQuestion(), context.relevantDocuments().size(), response.getProcessingTime());
                    
                    return response;
                });
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<String> ragChatStream(RagChatDto ragRequest, Long requesterUserId, boolean isAdmin) {
        log.info("🤖 [RAG模式] 开始流式对话: question={}, modelId={}, knowledgeBaseIds={}",
                ragRequest.getQuestion(), ragRequest.getModelId(), ragRequest.getKnowledgeBaseIds());
        
        return retrieveContext(
                ragRequest.getQuestion(), 
                ragRequest.getKnowledgeBaseIds(),
                ragRequest.getMaxRetrievedDocs() != null ? ragRequest.getMaxRetrievedDocs() : defaultMaxResults,
                ragRequest.getMinSimilarity() != null ? ragRequest.getMinSimilarity() : defaultMinScore,
                requesterUserId,
                isAdmin
        )
        .flatMapMany(context -> {
            // 构建增强提示词
            String enhancedPrompt = buildRagPrompt(ragRequest.getQuestion(), context.contextText());
            
            // 获取AI模型
            StreamingChatModel chatModel = aiModelRegistry.getStreamingModel(ragRequest.getModelId());
            if (chatModel == null) {
                return Flux.error(new IllegalArgumentException("未找到指定的AI模型: " + ragRequest.getModelId()));
            }
            
            // 返回流式响应
            return Flux.<String>create(sink -> {
                java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
                sink.onDispose(() -> {
                    log.info("RAG 流式会话已被下游取消订阅: question={}", ragRequest.getQuestion());
                    cancelled.set(true);
                });

                chatModel.chat(enhancedPrompt, 
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String partialResponse) {
                            if (cancelled.get()) {
                                throw new RuntimeException("StreamCancelledException");
                            }
                            sink.next(partialResponse);
                        }
                        
                        @Override
                        public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                            if (!cancelled.get()) {
                                sink.complete();
                            }
                        }
                        
                        @Override
                        public void onError(Throwable error) {
                            if (!cancelled.get()) {
                                sink.error(error);
                            }
                        }
                    });
            });
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<RetrievalContext> retrieveContext(String question, List<Long> knowledgeBaseIds, 
                                                  Integer maxResults, Double minSimilarity,
                                                  Long requesterUserId, boolean isAdmin) {
        long startTime = System.currentTimeMillis();
        
        return searchAcrossProfiles(
                question,
                knowledgeBaseIds,
                maxResults != null ? maxResults : defaultMaxResults,
                minSimilarity != null ? minSimilarity : defaultMinScore,
                requesterUserId,
                isAdmin)
            .map(searchResults -> {
                List<KnowledgeSearchResultDto.KnowledgeSearchMatch> relevantDocs = 
                    searchResults.stream()
                        .map(this::convertToSearchMatch)
                        .collect(Collectors.toList());
                
                // 构建上下文文本
                String contextText = relevantDocs.stream()
                    .map(doc -> String.format("文档：%s\n内容：%s\n", doc.getDocumentTitle(), doc.getContent()))
                    .collect(Collectors.joining("\n---\n"));
                
                long retrievalTime = System.currentTimeMillis() - startTime;
                
                log.info("🔍 [上下文检索] 上下文检索完成: question={}, documentCount={}, retrievalTime={}ms", 
                        question, relevantDocs.size(), retrievalTime);
                log.info("📝 [检索结果] 获得相关文档: {}", 
                        relevantDocs.stream().map(doc -> String.format("[%s:%.2f]", doc.getDocumentTitle(), doc.getScore())).collect(Collectors.joining(", ")));
                
                return new RetrievalContext(question, relevantDocs, contextText, relevantDocs.size(), retrievalTime);
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<List<EmbeddingService.EmbeddingSearchResult>> searchAcrossProfiles(
            String question, List<Long> knowledgeBaseIds, Integer maxResults, Double minSimilarity,
            Long requesterUserId, boolean isAdmin) {
        return resolveKnowledgeBases(knowledgeBaseIds, requesterUserId, isAdmin)
            .collectList()
            .flatMap(knowledgeBases -> {
                if (knowledgeBases.isEmpty()) {
                    return Mono.just(List.of());
                }

                // 1. 按 Profile 分组收集知识库 ID
                Map<EmbeddingProfile, List<Long>> idsByProfile = new LinkedHashMap<>();
                for (KnowledgeBase knowledgeBase : knowledgeBases) {
                    EmbeddingProfile profile = embeddingStoreRegistry.resolveProfile(knowledgeBase);
                    idsByProfile.computeIfAbsent(profile, ignored -> new java.util.ArrayList<>())
                            .add(knowledgeBase.getId());
                }

                // 2. 收集所有需要调用的唯一模型别名 (modelAlias)
                List<String> uniqueModelAliases = idsByProfile.keySet().stream()
                        .map(EmbeddingProfile::modelAlias)
                        .distinct()
                        .toList();

                // 3. 对每个唯一模型发起唯一的向量化请求，并收集成 Map<modelAlias, Embedding>
                return Flux.fromIterable(uniqueModelAliases)
                    .flatMap(alias -> embeddingService.embedText(TextSegment.from(question), alias)
                            .map(embedding -> Map.entry(alias, embedding)))
                    .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                    .flatMap(embeddingMap -> {
                        // 4. 使用已向量化好的 Embedding 映射并行执行各 Profile 的向量搜索，完全消除重复的 API 调用
                        return Flux.fromIterable(idsByProfile.entrySet())
                            .flatMap(entry -> {
                                dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingMap.get(entry.getKey().modelAlias());
                                if (queryEmbedding == null) {
                                    return Mono.just(List.<EmbeddingService.EmbeddingSearchResult>of());
                                }
                                return embeddingService.searchSimilarEmbeddings(
                                        queryEmbedding,
                                        entry.getValue(),
                                        maxResults,
                                        minSimilarity,
                                        entry.getKey().modelAlias(),
                                        entry.getKey().indexName());
                            })
                            .flatMapIterable(results -> results)
                            .sort(Comparator.comparing(EmbeddingService.EmbeddingSearchResult::score).reversed())
                            .take(maxResults)
                            .collectList();
                    });
            });
    }

    private Flux<KnowledgeBase> resolveKnowledgeBases(List<Long> knowledgeBaseIds,
                                                      Long requesterUserId, boolean isAdmin) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return knowledgeBaseRepository
                    .findByIsPublicAndStatusOrderByCreatedAtDesc(true, "ACTIVE", PageRequest.of(0, 5))
                    .doOnComplete(() -> log.debug("未指定知识库，仅使用最近 5 个公开活跃知识库进行 RAG"));
        }

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(knowledgeBaseIds);
        if (uniqueIds.contains(null) || uniqueIds.stream().anyMatch(id -> id <= 0)) {
            return Flux.error(new IllegalArgumentException("知识库ID必须为正数"));
        }

        return knowledgeBaseRepository.findAllById(uniqueIds)
                .collectList()
                .flatMapMany(knowledgeBases -> {
                    if (knowledgeBases.size() != uniqueIds.size()) {
                        return Flux.error(new IllegalArgumentException("部分知识库不存在"));
                    }
                    return Flux.fromIterable(knowledgeBases);
                })
                .concatMap(knowledgeBase -> authorizeKnowledgeBaseRead(knowledgeBase, requesterUserId, isAdmin));
    }

    private Mono<KnowledgeBase> authorizeKnowledgeBaseRead(KnowledgeBase knowledgeBase,
                                                           Long requesterUserId, boolean isAdmin) {
        if (!"ACTIVE".equalsIgnoreCase(knowledgeBase.getStatus())) {
            return Mono.error(new SecurityException("当前知识库不可用于检索"));
        }
        if (isAdmin) {
            return Mono.just(knowledgeBase);
        }
        if (requesterUserId == null || requesterUserId <= 0) {
            return Mono.error(new SecurityException("当前用户无权访问该知识库"));
        }
        boolean isOwner = requesterUserId.equals(knowledgeBase.getCreatorId());
        if (isOwner || Boolean.TRUE.equals(knowledgeBase.getIsPublic())) {
            return Mono.just(knowledgeBase);
        }
        return Mono.error(new SecurityException("当前用户无权访问该知识库"));
    }
    
    /**
     * 转换搜索结果为匹配对象
     */
    private KnowledgeSearchResultDto.KnowledgeSearchMatch convertToSearchMatch(
            EmbeddingService.EmbeddingSearchResult result) {
        
        KnowledgeSearchResultDto.KnowledgeSearchMatch match = 
            new KnowledgeSearchResultDto.KnowledgeSearchMatch();
        
        match.setChunkId(result.chunkId());
        match.setDocumentId(result.documentId());
        match.setKnowledgeBaseId(result.knowledgeBaseId());
        match.setContent(result.textSegment().text());
        match.setScore(result.score());
        match.setMatchTime(LocalDateTime.now());
        
        // 从TextSegment的元数据中获取更多信息
        var metadata = result.textSegment().metadata();
        match.setDocumentTitle(metadata.getString("document_title") != null ? metadata.getString("document_title") : "未知文档");
        match.setKnowledgeBaseName(metadata.getString("knowledge_base_name") != null ? metadata.getString("knowledge_base_name") : "未知知识库");
        match.setChunkIndex(metadata.getInteger("chunk_index") != null ? metadata.getInteger("chunk_index") : 0);
        
        return match;
    }
    
    /**
     * 构建RAG提示词
     */
    private String buildRagPrompt(String question, String contextText) {
        return String.format("""
                请基于以下提供的知识库内容来回答用户的问题。如果知识库中没有相关信息，请诚实说明。
                
                知识库内容：
                %s
                
                用户问题：%s
                
                要求：
                1. 优先使用知识库中的确切信息进行回答。
                2. 回答要直接、自然、流畅，不要生硬地罗列。
                3. 绝对不要在回答中包含“根据知识库内容”、“引用自知识库”等套话，也不要单独罗列引用来源，直接告诉用户答案即可。
                """, contextText, question);
    }
    
    /**
     * 生成AI响应
     */
    private Mono<String> generateResponse(StreamingChatModel chatModel, String prompt) {
        return Mono.fromFuture(() -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            StringBuilder responseBuilder = new StringBuilder();
            
            chatModel.chat(prompt, 
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        responseBuilder.append(partialResponse);
                    }
                    
                    @Override
                    public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                        future.complete(responseBuilder.toString());
                    }
                    
                    @Override
                    public void onError(Throwable error) {
                        future.completeExceptionally(error);
                    }
                });
            
            return future;
        });
    }
}
