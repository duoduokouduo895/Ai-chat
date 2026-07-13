package org.example.ai_agent.service.impl.document;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.properties.DocumentProcessingProperties;
import org.example.ai_agent.service.DocumentParserService;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 文档解析服务实现类
 * <p>
 * 基于LangChain4j 1.0.1的文档处理架构，集成Apache Tika和硅基流动API
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Service
public class DocumentParserServiceImpl implements DocumentParserService {
    
    private final DocumentParser documentParser;
    private final DocumentSplitter documentSplitter;
    private final AiModelRegistry aiModelRegistry;
    private final DocumentProcessingProperties processingProperties;
    private final Scheduler documentParsingScheduler;
    
    @Value("${knowledge-base.document.supported-types}")
    private String[] supportedTypes;

    public DocumentParserServiceImpl(DocumentParser documentParser,
                                     DocumentSplitter documentSplitter,
                                     AiModelRegistry aiModelRegistry,
                                     DocumentProcessingProperties processingProperties,
                                     @Qualifier("documentParsingScheduler") Scheduler documentParsingScheduler) {
        this.documentParser = documentParser;
        this.documentSplitter = documentSplitter;
        this.aiModelRegistry = aiModelRegistry;
        this.processingProperties = processingProperties;
        this.documentParsingScheduler = documentParsingScheduler;
    }
    
    @Override
    public Mono<Document> parseDocument(InputStream inputStream, String fileName, String fileType) {
        return Mono.fromCallable(() -> {
            try {
                int fileNameLength = fileName == null ? 0 : fileName.length();
                log.info("开始解析文档: fileNameLength={}, fileType={}",
                        fileNameLength, fileType);
                
                // 使用LangChain4j的DocumentParser解析文档
                Document document = documentParser.parse(inputStream);
                
                // 添加文件相关的元数据
                Metadata metadata = document.metadata();
                metadata.put(Document.FILE_NAME, fileName);
                metadata.put("file_type", fileType);
                metadata.put("parse_method", "apache_tika");
                metadata.put("parse_time", System.currentTimeMillis());
                
                String text = enforceExtractedTextLimit(document.text());
                log.info("文档解析成功: contentLength={}", text.length());
                
                return Document.from(text, metadata);
                
            } catch (DocumentParsingLimitExceededException error) {
                throw error;
            } catch (Exception e) {
                log.error("文档解析失败: errorType={}", e.getClass().getSimpleName());
                throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
            }
        }).subscribeOn(documentParsingScheduler);
    }
    
    @Override
    public Mono<List<TextSegment>> splitDocument(Document document) {
        return Mono.fromCallable(() -> {
            try {
                log.info("开始分割文档: contentLength={}", document.text().length());
                
                // 使用LangChain4j的DocumentSplitter分割文档
                List<TextSegment> segments = documentSplitter.split(document);
                
                log.info("文档分割完成: segmentCount={}", segments.size());
                
                return segments;
                
            } catch (DocumentParsingLimitExceededException error) {
                throw error;
            } catch (Exception e) {
                log.error("文档分割失败: errorType={}", e.getClass().getSimpleName());
                throw new RuntimeException("文档分割失败: " + e.getMessage(), e);
            }
        }).subscribeOn(documentParsingScheduler);
    }
    
    @Override
    public Mono<Document> parseDocumentWithAI(InputStream inputStream, String fileName, 
                                            String fileType, String modelId) {
        return Mono.fromCallable(() -> {
            try {
                log.info("开始使用AI解析文档: fileType={}, modelId={}", fileType, modelId);
                
                // 首先尝试基础解析
                Document basicDocument = documentParser.parse(inputStream);
                
                // 如果基础解析内容太少或质量不高，使用AI增强解析
                String basicText = enforceExtractedTextLimit(basicDocument.text());
                boolean shouldEnhance = needAIEnhancement(basicText, fileType);
                if (shouldEnhance
                        && basicText.length() <= processingProperties.effectiveMaxAiEnhancementCharacters()) {
                    String enhancedText = enhanceWithAI(basicText, fileName, fileType, modelId);
                    
                    Metadata metadata = basicDocument.metadata();
                    metadata.put(Document.FILE_NAME, fileName);
                    metadata.put("file_type", fileType);
                    metadata.put("parse_method", "ai_enhanced");
                    metadata.put("ai_model", modelId);
                    metadata.put("parse_time", System.currentTimeMillis());
                    
                    log.info("AI增强解析完成: originalLength={}, enhancedLength={}",
                            basicText.length(), enhancedText.length());
                    
                    return Document.from(enforceExtractedTextLimit(enhancedText), metadata);
                } else {
                    if (shouldEnhance) {
                        log.info("跳过超长文档的 AI 增强解析: contentLength={}, limit={}",
                                basicText.length(), processingProperties.effectiveMaxAiEnhancementCharacters());
                    }
                    // 使用基础解析结果
                    Metadata metadata = basicDocument.metadata();
                    metadata.put(Document.FILE_NAME, fileName);
                    metadata.put("file_type", fileType);
                    metadata.put("parse_method", "apache_tika");
                    metadata.put("parse_time", System.currentTimeMillis());
                    
                    return Document.from(basicText, metadata);
                }
                
            } catch (DocumentParsingLimitExceededException error) {
                throw error;
            } catch (Exception e) {
                log.error("AI文档解析失败: errorType={}", e.getClass().getSimpleName());
                throw new RuntimeException("AI文档解析失败: " + e.getMessage(), e);
            }
        }).subscribeOn(documentParsingScheduler);
    }
    
    @Override
    public boolean isSupportedFileType(String fileType) {
        if (fileType == null) {
            return false;
        }
        return Arrays.asList(supportedTypes).contains(fileType.toLowerCase());
    }
    
    @Override
    public List<String> getSupportedFileTypes() {
        return Arrays.asList(supportedTypes);
    }
    
    /**
     * 判断是否需要AI增强解析
     */
    private boolean needAIEnhancement(String text, String fileType) {
        // 如果文本太短，可能解析不完整
        if (text.length() < 100) {
            return true;
        }
        
        // 对于特定文件类型，建议使用AI增强
        if ("pdf".equalsIgnoreCase(fileType) || "pptx".equalsIgnoreCase(fileType)) {
            // 检查是否包含大量无意义字符或格式混乱
            long specialCharCount = text.chars().filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch)).count();
            double specialCharRatio = (double) specialCharCount / text.length();
            return specialCharRatio > 0.3; // 如果特殊字符超过30%，可能需要增强
        }
        
        return false;
    }
    
    /**
     * 使用AI模型增强文档解析
     */
    private String enhanceWithAI(String originalText, String fileName, String fileType, String modelId) {
        try {
            StreamingChatModel chatModel = aiModelRegistry.getStreamingModel(
                    modelId != null ? modelId : aiModelRegistry.getDefaultChatModel());
            if (chatModel == null) {
                log.warn("未找到指定的AI模型: {}, 使用原始解析结果", modelId);
                return originalText;
            }
            
            String prompt = buildDocumentEnhancementPrompt(originalText, fileName, fileType);
            
            // 使用CompletableFuture收集流式响应
            CompletableFuture<String> future = new CompletableFuture<>();
            StringBuilder responseBuilder = new StringBuilder();
            
            chatModel.chat(prompt, 
                new dev.langchain4j.model.chat.response.StreamingChatResponseHandler() {
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
            
            String enhancedText = future.get(
                    processingProperties.effectiveAiEnhancementTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return enhancedText.length() > originalText.length() ? enhancedText : originalText;
            
        } catch (Exception e) {
            log.error("AI增强解析失败，使用原始解析结果: errorType={}", e.getClass().getSimpleName());
            return originalText;
        }
    }
    
    /**
     * 构建文档增强解析的提示词
     */
    private String buildDocumentEnhancementPrompt(String originalText, String fileName, String fileType) {
        return String.format("""
                请帮我清理和整理以下从%s文件（%s）中提取的文本内容。
                
                原始提取的文本可能包含格式混乱、乱码或无意义的字符。
                请你：
                1. 清理无意义的字符和格式符号
                2. 整理文本结构，保持逻辑清晰
                3. 修正可能的文字识别错误
                4. 保留所有有意义的内容信息
                5. 输出整理后的完整文本内容
                
                原始文本：
                %s
                
                请直接输出整理后的文本内容：
                """, fileType, fileName, originalText);
    }

    private String enforceExtractedTextLimit(String text) {
        String safeText = text == null ? "" : text;
        if (safeText.length() > processingProperties.effectiveMaxExtractedCharacters()) {
            throw new DocumentParsingLimitExceededException(processingProperties.effectiveMaxExtractedCharacters());
        }
        return safeText;
    }
}
