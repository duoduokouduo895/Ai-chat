package org.example.ai_agent.service.impl.knowledge;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchDto;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchResultDto;
import org.example.ai_agent.dto.chat.RagChatDto;
import org.example.ai_agent.dto.chat.RagChatResponseDto;
import org.example.ai_agent.service.EmbeddingService;
import org.example.ai_agent.service.RagService;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;
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
 * @since 2024
 */
@Slf4j
@Service
public class RagServiceImpl implements RagService {
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private AiModelRegistry aiModelRegistry;
    
    @Value("${knowledge-base.embedding.default-model:qwen-embedding-8b}")
    private String defaultEmbeddingModel;
    
    @Value("${knowledge-base.rag.max-results:10}")
    private Integer defaultMaxResults;
    
    @Value("${knowledge-base.rag.min-score:0.7}")
    private Double defaultMinScore;
    
    @Override
    public Mono<KnowledgeSearchResultDto> searchKnowledge(KnowledgeSearchDto searchRequest) {
        long startTime = System.currentTimeMillis();
        
        return Mono.fromCallable(() -> {
            // 创建查询的文本段
            TextSegment querySegment = TextSegment.from(searchRequest.getQuery());
            return querySegment;
        })
        .flatMap(querySegment -> {
            // 向量化查询文本
            return embeddingService.embedText(querySegment, defaultEmbeddingModel);
        })
        .flatMap(queryEmbedding -> {
            // 执行向量搜索
            return embeddingService.searchSimilarEmbeddings(
                queryEmbedding,
                searchRequest.getKnowledgeBaseIds(),
                searchRequest.getMaxResults() != null ? searchRequest.getMaxResults() : defaultMaxResults,
                searchRequest.getMinScore() != null ? searchRequest.getMinScore() : defaultMinScore
            );
        })
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
    public Mono<RagChatResponseDto> ragChat(RagChatDto ragRequest) {
        long startTime = System.currentTimeMillis();
        
        return retrieveContext(
                ragRequest.getQuestion(), 
                ragRequest.getKnowledgeBaseIds(),
                ragRequest.getMaxRetrievedDocs() != null ? ragRequest.getMaxRetrievedDocs() : defaultMaxResults,
                ragRequest.getMinSimilarity() != null ? ragRequest.getMinSimilarity() : defaultMinScore
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
    public Flux<String> ragChatStream(RagChatDto ragRequest) {
        return retrieveContext(
                ragRequest.getQuestion(), 
                ragRequest.getKnowledgeBaseIds(),
                ragRequest.getMaxRetrievedDocs() != null ? ragRequest.getMaxRetrievedDocs() : defaultMaxResults,
                ragRequest.getMinSimilarity() != null ? ragRequest.getMinSimilarity() : defaultMinScore
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
                chatModel.chat(enhancedPrompt, 
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String partialResponse) {
                            sink.next(partialResponse);
                        }
                        
                        @Override
                        public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                            sink.complete();
                        }
                        
                        @Override
                        public void onError(Throwable error) {
                            sink.error(error);
                        }
                    });
            });
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<RetrievalContext> retrieveContext(String question, List<Long> knowledgeBaseIds, 
                                                Integer maxResults, Double minSimilarity) {
        long startTime = System.currentTimeMillis();
        
        return Mono.fromCallable(() -> TextSegment.from(question))
            .flatMap(querySegment -> embeddingService.embedText(querySegment, defaultEmbeddingModel))
            .flatMap(queryEmbedding -> {
                return embeddingService.searchSimilarEmbeddings(
                    queryEmbedding, 
                    knowledgeBaseIds, 
                    maxResults != null ? maxResults : defaultMaxResults,
                    minSimilarity != null ? minSimilarity : defaultMinScore
                );
            })
            .map(searchResults -> {
                List<KnowledgeSearchResultDto.KnowledgeSearchMatch> relevantDocs = 
                    searchResults.stream()
                        .map(this::convertToSearchMatch)
                        .collect(Collectors.toList());
                
                // 构建上下文文本
                String contextText = relevantDocs.stream()
                    .map(doc -> String.format("文档：%s\\n内容：%s\\n", doc.getDocumentTitle(), doc.getContent()))
                    .collect(Collectors.joining("\\n---\\n"));
                
                long retrievalTime = System.currentTimeMillis() - startTime;
                
                log.info("上下文检索完成: question={}, documentCount={}, retrievalTime={}ms", 
                        question, relevantDocs.size(), retrievalTime);
                
                return new RetrievalContext(question, relevantDocs, contextText, relevantDocs.size(), retrievalTime);
            })
            .subscribeOn(Schedulers.boundedElastic());
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
                
                请提供准确、有用的回答，并尽可能引用具体的知识库内容：
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