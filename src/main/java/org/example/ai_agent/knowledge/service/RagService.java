package org.example.ai_agent.knowledge.service;

import org.example.ai_agent.knowledge.dto.KnowledgeSearchDto;
import org.example.ai_agent.knowledge.dto.KnowledgeSearchResultDto;
import org.example.ai_agent.chat.dto.RagChatDto;
import org.example.ai_agent.chat.dto.RagChatResponseDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * RAG检索增强生成服务接口
 * <p>
 * 基于LangChain4j 1.0.1的DefaultRetrievalAugmentor设计
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
public interface RagService {
    String INSUFFICIENT_EVIDENCE = "当前知识库中没有找到可用资料，暂时无法依据资料回答这个问题。";
    
    /**
     * 知识库搜索
     * 
     * @param searchRequest 搜索请求
     * @return 搜索结果
     */
    Mono<KnowledgeSearchResultDto> searchKnowledge(KnowledgeSearchDto searchRequest,
                                                   Long requesterUserId, boolean isAdmin);
    
    /**
     * RAG对话 - 非流式
     * 
     * @param ragRequest RAG请求
     * @return 对话响应
     */
    Mono<RagChatResponseDto> ragChat(RagChatDto ragRequest, Long requesterUserId, boolean isAdmin);
    
    /**
     * RAG对话 - 流式
     * 
     * @param ragRequest RAG请求
     * @return 流式响应
     */
    Flux<String> ragChatStream(RagChatDto ragRequest, Long requesterUserId, boolean isAdmin);
    
    /**
     * 获取问题的相关上下文
     * 
     * @param question 用户问题
     * @param knowledgeBaseIds 知识库ID列表
     * @param maxResults 最大结果数
     * @param minSimilarity 最小相似度
     * @return 相关上下文
     */
    Mono<RetrievalContext> retrieveContext(String question, java.util.List<Long> knowledgeBaseIds,
                                           Integer maxResults, Double minSimilarity,
                                           Long requesterUserId, boolean isAdmin);
    
    /**
     * 检索上下文信息
     */
    record RetrievalContext(
            String question,
            java.util.List<KnowledgeSearchResultDto.KnowledgeSearchMatch> relevantDocuments,
            String contextText,
            Integer documentCount,
            Long retrievalTime,
            org.example.ai_agent.knowledge.domain.RetrievalOutcome.Status status,
            java.util.List<org.example.ai_agent.knowledge.domain.RetrievalOutcome.Channel> channels
    ) {
        public RetrievalContext(String question, java.util.List<KnowledgeSearchResultDto.KnowledgeSearchMatch> relevantDocuments,
                String contextText, Integer documentCount, Long retrievalTime) {
            this(question, relevantDocuments, contextText, documentCount, retrievalTime,
                    relevantDocuments.isEmpty() ? org.example.ai_agent.knowledge.domain.RetrievalOutcome.Status.EMPTY
                            : org.example.ai_agent.knowledge.domain.RetrievalOutcome.Status.FOUND, java.util.List.of());
        }
    }
}
