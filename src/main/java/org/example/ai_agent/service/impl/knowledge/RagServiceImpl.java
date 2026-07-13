package org.example.ai_agent.service.impl.knowledge;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchDto;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchResultDto;
import org.example.ai_agent.dto.chat.RagChatDto;
import org.example.ai_agent.dto.chat.RagChatResponseDto;
import org.example.ai_agent.service.HybridRetrievalService;
import org.example.ai_agent.service.RagService;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    private final AiModelRegistry aiModelRegistry;
    private final HybridRetrievalService hybridRetrievalService;
    private final RetrievalContextAssembler retrievalContextAssembler;

    public RagServiceImpl(AiModelRegistry aiModelRegistry,
                          HybridRetrievalService hybridRetrievalService,
                          RetrievalContextAssembler retrievalContextAssembler) {
        this.aiModelRegistry = aiModelRegistry;
        this.hybridRetrievalService = hybridRetrievalService;
        this.retrievalContextAssembler = retrievalContextAssembler;
    }
    
    @Value("${knowledge-base.rag.max-results:10}")
    private Integer defaultMaxResults;
    
    @Value("${knowledge-base.rag.min-score:0.7}")
    private Double defaultMinScore;
    
    @Override
    public Mono<KnowledgeSearchResultDto> searchKnowledge(KnowledgeSearchDto searchRequest,
                                                          Long requesterUserId, boolean isAdmin) {
        long startTime = System.currentTimeMillis();
        
        return hybridRetrievalService.retrieve(
                searchRequest.getQuery(),
                searchRequest.getKnowledgeBaseIds(),
                searchRequest.getMaxResults() != null ? searchRequest.getMaxResults() : defaultMaxResults,
                searchRequest.getMinScore() != null ? searchRequest.getMinScore() : defaultMinScore,
                requesterUserId,
                isAdmin
            )
        .map(searchResults -> {
            KnowledgeSearchResultDto result =
                    retrievalContextAssembler.toSearchResult(searchResults, startTime);

            log.info("知识库搜索完成: queryLength={}, resultCount={}, searchTime={}ms",
                    searchRequest.getQuery().length(), result.getTotalCount(), result.getSearchTime());

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
                    
                    log.info("RAG对话完成: questionLength={}, sourceCount={}, processingTime={}ms",
                            ragRequest.getQuestion().length(), context.relevantDocuments().size(), response.getProcessingTime());
                    
                    return response;
                });
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<String> ragChatStream(RagChatDto ragRequest, Long requesterUserId, boolean isAdmin) {
        log.info("🤖 [RAG模式] 开始流式对话: questionLength={}, modelId={}, knowledgeBaseIds={}",
                ragRequest.getQuestion().length(), ragRequest.getModelId(), ragRequest.getKnowledgeBaseIds());
        
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
                    log.info("RAG 流式会话已被下游取消订阅");
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
        int questionLength = question == null ? 0 : question.length();
        
        return hybridRetrievalService.retrieve(
                question,
                knowledgeBaseIds,
                maxResults != null ? maxResults : defaultMaxResults,
                minSimilarity != null ? minSimilarity : defaultMinScore,
                requesterUserId,
                isAdmin)
            .map(searchResults -> {
                RetrievalContext context =
                        retrievalContextAssembler.toRetrievalContext(question, searchResults, startTime);

                log.info("🔍 [上下文检索] 上下文检索完成: questionLength={}, documentCount={}, retrievalTime={}ms",
                        questionLength, context.documentCount(), context.retrievalTime());

                return context;
            })
            .subscribeOn(Schedulers.boundedElastic());
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
