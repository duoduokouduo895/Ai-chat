package org.example.ai_agent.constants;

/**
 * 文档处理相关常量
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
public final class DocumentProcessingConstants {
    
    /**
     * 消息队列相关常量
     */
    public static final class Queue {
        /** 文档解析队列 */
        public static final String DOCUMENT_PARSE = "document.parse";
        /** 文档解析重试队列 */
        public static final String DOCUMENT_PARSE_RETRY = "document.parse.retry";
        /** 文档分块队列 */
        public static final String DOCUMENT_CHUNK = "document.chunk";
        /** 文档分块重试队列 */
        public static final String DOCUMENT_CHUNK_RETRY = "document.chunk.retry";
        /** 文档向量化队列 */
        public static final String DOCUMENT_EMBED = "document.embed";
        /** 文档向量化重试队列 */
        public static final String DOCUMENT_EMBED_RETRY = "document.embed.retry";
        /** 死信队列 */
        public static final String DOCUMENT_DLQ = "document.dlq";
        
        private Queue() {}
    }
    
    /**
     * 交换机相关常量
     */
    public static final class Exchange {
        /** 文档处理交换机 */
        public static final String DOCUMENT_PROCESSING = "document.processing";
        /** 死信交换机 */
        public static final String DOCUMENT_DLX = "document.dlx";
        
        private Exchange() {}
    }
    
    /**
     * 路由键相关常量
     */
    public static final class RoutingKey {
        /** 文档解析路由键 */
        public static final String DOCUMENT_PARSE = "document.parse";
        /** 文档解析重试路由键 */
        public static final String DOCUMENT_PARSE_RETRY = "document.parse.retry";
        /** 文档分块路由键 */
        public static final String DOCUMENT_CHUNK = "document.chunk";
        /** 文档分块重试路由键 */
        public static final String DOCUMENT_CHUNK_RETRY = "document.chunk.retry";
        /** 文档向量化路由键 */
        public static final String DOCUMENT_EMBED = "document.embed";
        /** 文档向量化重试路由键 */
        public static final String DOCUMENT_EMBED_RETRY = "document.embed.retry";
        /** 死信路由键 */
        public static final String DOCUMENT_DLQ = "document.dlq";
        
        private RoutingKey() {}
    }
    
    /**
     * 处理状态常量
     */
    public static final class Status {
        /** 待处理 */
        public static final String PENDING = "PENDING";
        /** 处理中 */
        public static final String PROCESSING = "PROCESSING";
        /** 已完成 */
        public static final String COMPLETED = "COMPLETED";
        /** 失败 */
        public static final String FAILED = "FAILED";
        
        private Status() {}
    }
    
    /**
     * 解析模式常量
     */
    public static final class ParseMode {
        /** 自动解析 */
        public static final String AUTO = "auto";
        /** 基础解析 */
        public static final String BASIC = "basic";
        /** AI增强解析 */
        public static final String AI_ENHANCED = "ai_enhanced";
        
        private ParseMode() {}
    }
    
    /**
     * 重试相关常量
     */
    public static final class Retry {
        /** 最大重试次数 */
        public static final int MAX_ATTEMPTS = 3;
        /** 重试间隔（毫秒） */
        public static final long RETRY_DELAY_MS = 5000;
        
        private Retry() {}
    }
    
    private DocumentProcessingConstants() {}
}
