package org.example.ai_agent.config.ai;

import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.example.ai_agent.config.ai.AiModelProperties;
import org.example.ai_agent.config.database.ElasticsearchConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;

/**
 * LangChain4j知识库配置类
 * <p>
 * 基于LangChain4j 1.0.1源码分析，配置文档解析、向量化、RAG等核心组件
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
@Slf4j
@Configuration
public class KnowledgeBaseConfig {
    
    @Autowired
    private AiModelProperties aiModelProperties;
    
    @Autowired
    private ElasticsearchConfig elasticsearchConfig;
    
    @Value("${knowledge-base.embedding.default-model:qwen-embedding-8b}")
    private String defaultEmbeddingModel;
    
    @Value("${knowledge-base.document.max-segment-size:500}")
    private Integer maxSegmentSize;
    
    @Value("${knowledge-base.document.max-overlap-size:50}")
    private Integer maxOverlapSize;
    
    @Value("${knowledge-base.embedding.index-prefix:kb_}")
    private String embeddingIndexPrefix;
    
    /**
     * 配置文档解析器
     * 基于Apache Tika，支持PDF、DOCX、TXT、MD等多种格式
     */
    @Bean
    public DocumentParser documentParser() {
        log.info("初始化文档解析器: Apache Tika");
        return new ApacheTikaDocumentParser(true); // 包含元数据
    }
    
    /**
     * 配置文档分割器
     * 基于LangChain4j的递归分割策略
     */
    @Bean
    public DocumentSplitter documentSplitter() {
        log.info("初始化文档分割器: maxSegmentSize={}, maxOverlapSize={}", 
                maxSegmentSize, maxOverlapSize);
        return DocumentSplitters.recursive(maxSegmentSize, maxOverlapSize);
    }
    
    /**
     * 配置Elasticsearch向量存储
     * 使用LangChain4j 1.0.1推荐的RestClient构造方式，避免已弃用的serverUrl方法
     */
    @Bean
    public EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore() {
        log.info("初始化Elasticsearch向量存储");
        
        try {
            // 直接使用配置文件中的URI配置，避免类型转换错误
            String elasticsearchUri = elasticsearchConfig.getElasticsearchUri();
            log.info("使用Elasticsearch URI: {}", elasticsearchUri);
            
            // 解析URI提取主机和端口
            String cleanUri = elasticsearchUri.replace("http://", "").replace("https://", "");
            String host;
            int port = 9200; // 默认端口
            
            if (cleanUri.contains(":")) {
                String[] parts = cleanUri.split(":");
                host = parts[0];
                try {
                    port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    log.warn("无法解析端口号，使用默认端口 9200: {}", parts[1]);
                }
            } else {
                host = cleanUri;
            }
            
            log.info("解析后的连接信息: host={}, port={}", host, port);
            
            // 创建HttpHost和RestClient
            HttpHost httpHost = new HttpHost(host, port, "http");
            
            // 获取超时配置
            int connectTimeout = elasticsearchConfig.getConnectionTimeout();
            int socketTimeout = elasticsearchConfig.getSocketTimeout();
            
            RestClient restClient = RestClient.builder(httpHost)
                    .setRequestConfigCallback(requestConfigBuilder -> {
                        return requestConfigBuilder
                                .setConnectTimeout(connectTimeout)
                                .setSocketTimeout(socketTimeout);
                    })
                    .build();
            
            log.info("Elasticsearch RestClient 初始化完成: {}:{}", host, port);
            
            // 测试连接
            try {
                org.elasticsearch.client.Response response = restClient.performRequest(
                    new org.elasticsearch.client.Request("GET", "/"));
                log.info("Elasticsearch连接测试成功，响应状态: {}", response.getStatusLine().getStatusCode());
            } catch (Exception e) {
                log.warn("Elasticsearch连接测试失败，但继续初始化: {}", e.getMessage());
            }
            
            // 使用LangChain4j 1.0.1推荐的restClient()方法创建ElasticsearchEmbeddingStore
            ElasticsearchEmbeddingStore embeddingStore = ElasticsearchEmbeddingStore.builder()
                    .restClient(restClient)
                    .indexName("knowledge_base_embeddings")
                    .build();
            
            log.info("ElasticsearchEmbeddingStore 初始化成功");
            return embeddingStore;
            
        } catch (Exception e) {
            log.error("Elasticsearch向量存储初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("无法初始化Elasticsearch向量存储: " + e.getMessage(), e);
        }
    }
    
    /**
     * 配置内容检索器
     * 用于RAG检索 - 动态创建EmbeddingModel
     */
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore) {
        log.info("初始化内容检索器");
        
        // 动态创建EmbeddingModel实例
        EmbeddingModel embeddingModel = createQwenEmbeddingModel();
        
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(10)
                .minScore(0.7)
                .build();
    }
    
    /**
     * 创建Qwen嵌入模型实例
     */
    private EmbeddingModel createQwenEmbeddingModel() {
        String globalApiKey = aiModelProperties.getApiKey();
        String globalBaseUrl = aiModelProperties.getBaseUrl();
        
        log.info("创建Qwen嵌入模型: Qwen/Qwen3-Embedding-8B");
        
        return OpenAiEmbeddingModel.builder()
                .apiKey(globalApiKey)
                .baseUrl(globalBaseUrl)
                .modelName("Qwen/Qwen3-Embedding-8B")
                .build();
    }
    
    /**
     * 配置RAG检索增强器
     * 核心RAG实现
     */
    @Bean
    public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever) {
        log.info("初始化RAG检索增强器");
        
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .build();
    }
}