package org.example.ai_agent.config.ai;

import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j知识库配置类
 * <p>
 * 基于LangChain4j 1.0.1源码分析，配置文档解析、向量化、RAG等核心组件
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Configuration
public class KnowledgeBaseConfig {
    
    @Value("${knowledge-base.document.max-segment-size:500}")
    private Integer maxSegmentSize;
    
    @Value("${knowledge-base.document.max-overlap-size:50}")
    private Integer maxOverlapSize;
    
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
    
}
