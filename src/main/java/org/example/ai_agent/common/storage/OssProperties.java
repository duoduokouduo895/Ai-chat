package org.example.ai_agent.common.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云OSS配置属性
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {
    
    /**
     * OSS Endpoint
     */
    private String endpoint;
    
    /**
     * Access Key ID
     */
    private String accessKeyId;
    
    /**
     * Access Key Secret
     */
    private String accessKeySecret;
    
    /**
     * Bucket名称
     */
    private String bucketName;
    
    /**
     * 文件URL前缀
     */
    private String urlPrefix;
    
    /**
     * 知识库文件存储路径前缀
     */
    private String knowledgeBasePrefix = "knowledge-base/";
}

/**
 * 知识库配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge-base")
class KnowledgeBaseProperties {
    
    /**
     * 文档解析配置
     */
    private DocumentConfig document = new DocumentConfig();
    
    /**
     * 向量化配置
     */
    private EmbeddingConfig embedding = new EmbeddingConfig();
    
    /**
     * RAG配置
     */
    private RagConfig rag = new RagConfig();
    
    @Data
    public static class DocumentConfig {
        /**
         * 最大文件大小(MB)
         */
        private Long maxFileSizeMB = 50L;
        
        /**
         * 支持的文件类型
         */
        private String[] supportedTypes = {"pdf", "docx", "doc", "txt", "md"};
        
        /**
         * 文档分块最大大小(字符数)
         */
        private Integer maxSegmentSize = 500;
        
        /**
         * 文档分块重叠大小(字符数)
         */
        private Integer maxOverlapSize = 50;
    }
    
    @Data
    public static class EmbeddingConfig {
        /**
         * 默认向量化模型
         */
        private String defaultModel = "glm-air";
        
        /**
         * Elasticsearch索引前缀
         */
        private String indexPrefix = "kb_";
        
        /**
         * 批量处理大小
         */
        private Integer batchSize = 100;
    }
    
    @Data
    public static class RagConfig {
        /**
         * 默认检索文档数量
         */
        private Integer maxResults = 10;
        
        /**
         * 最小相似度分数
         */
        private Double minScore = 0.7;
        
        /**
         * 启用重排序
         */
        private Boolean enableReranking = false;
    }
}