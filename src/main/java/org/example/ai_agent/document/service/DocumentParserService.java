package org.example.ai_agent.document.service;

import dev.langchain4j.data.document.Document;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.List;

/**
 * 文档解析服务接口
 * <p>
 * 基于LangChain4j的Document接口，集成硅基流动API进行文档解析
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
public interface DocumentParserService {
    
    /**
     * 解析文档
     * 
     * @param inputStream 文件输入流
     * @param fileName 文件名
     * @param fileType 文件类型
     * @return 解析后的文档对象
     */
    Mono<Document> parseDocument(InputStream inputStream, String fileName, String fileType);
    
    /**
     * 分割文档为文本块
     * 
     * @param document 文档对象
     * @return 文本块列表
     */
    Mono<List<dev.langchain4j.data.segment.TextSegment>> splitDocument(Document document);
    
    /**
     * 使用AI模型解析复杂文档
     * 主要用于处理Apache Tika无法很好解析的文档
     * 
     * @param inputStream 文件输入流
     * @param fileName 文件名
     * @param fileType 文件类型
     * @param modelId AI模型ID
     * @return 解析后的文档对象
     */
    Mono<Document> parseDocumentWithAI(InputStream inputStream, String fileName, String fileType, String modelId);
    
    /**
     * 检查文件类型是否支持
     * 
     * @param fileType 文件类型
     * @return 是否支持
     */
    boolean isSupportedFileType(String fileType);
    
    /**
     * 获取支持的文件类型列表
     * 
     * @return 支持的文件类型
     */
    List<String> getSupportedFileTypes();
}