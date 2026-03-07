package org.example.ai_agent.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 向量化服务接口
 * <p>
 * 基于LangChain4j 1.0.1的EmbeddingModel和EmbeddingStore设计
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
public interface EmbeddingService {
    
    /**
     * 对文本段进行向量化
     * 
     * @param textSegment 文本段
     * @param modelId 向量化模型ID
     * @return 向量对象
     */
    Mono<Embedding> embedText(TextSegment textSegment, String modelId);
    
    /**
     * 批量对文本段进行向量化
     * 
     * @param textSegments 文本段列表
     * @param modelId 向量化模型ID
     * @return 向量对象列表
     */
    Mono<List<Embedding>> embedTexts(List<TextSegment> textSegments, String modelId);
    
    /**
     * 存储向量到Elasticsearch
     * 
     * @param knowledgeBaseId 知识库ID
     * @param documentId 文档ID
     * @param chunkId 文档块ID
     * @param embedding 向量对象
     * @param textSegment 原始文本段
     * @return 向量存储ID
     */
    Mono<String> storeEmbedding(Long knowledgeBaseId, Long documentId, Long chunkId, 
                              Embedding embedding, TextSegment textSegment);
    
    /**
     * 批量存储向量
     * 
     * @param knowledgeBaseId 知识库ID
     * @param documentId 文档ID
     * @param chunkIds 文档块ID列表
     * @param embeddings 向量列表
     * @param textSegments 文本段列表
     * @return 存储ID列表
     */
    Mono<List<String>> storeEmbeddings(Long knowledgeBaseId, Long documentId, List<Long> chunkIds,
                                     List<Embedding> embeddings, List<TextSegment> textSegments);
    
    /**
     * 搜索相似向量
     * 
     * @param queryEmbedding 查询向量
     * @param knowledgeBaseIds 知识库ID列表（可选）
     * @param maxResults 最大结果数
     * @param minScore 最小相似度分数
     * @return 搜索结果
     */
    Mono<List<EmbeddingSearchResult>> searchSimilarEmbeddings(Embedding queryEmbedding, 
                                                            List<Long> knowledgeBaseIds,
                                                            Integer maxResults, 
                                                            Double minScore);
    
    /**
     * 删除文档的所有向量
     * 
     * @param documentId 文档ID
     * @return 删除结果
     */
    Mono<Boolean> deleteDocumentEmbeddings(Long documentId);
    
    /**
     * 删除知识库的所有向量
     * 
     * @param knowledgeBaseId 知识库ID
     * @return 删除结果
     */
    Mono<Boolean> deleteKnowledgeBaseEmbeddings(Long knowledgeBaseId);
    
    /**
     * 向量搜索结果
     */
    record EmbeddingSearchResult(
            String embeddingId,
            Long knowledgeBaseId,
            Long documentId, 
            Long chunkId,
            TextSegment textSegment,
            Double score
    ) {}
}