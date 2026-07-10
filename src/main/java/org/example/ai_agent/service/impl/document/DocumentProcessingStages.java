package org.example.ai_agent.service.impl.document;

import dev.langchain4j.data.document.Document;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.entity.KnowledgeDocument;
import org.example.ai_agent.service.DocumentChunkService;
import org.example.ai_agent.service.DocumentParserService;
import org.example.ai_agent.service.KnowledgeChunkSearchService;
import org.example.ai_agent.service.OssService;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DocumentProcessingStages {

    private final DocumentParserService documentParserService;
    private final DocumentChunkService documentChunkService;
    private final OssService ossService;
    private final KnowledgeChunkSearchService knowledgeChunkSearchService;
    private final AiModelRegistry aiModelRegistry;

    public DocumentProcessingStages(DocumentParserService documentParserService,
                                    DocumentChunkService documentChunkService,
                                    OssService ossService,
                                    KnowledgeChunkSearchService knowledgeChunkSearchService,
                                    AiModelRegistry aiModelRegistry) {
        this.documentParserService = documentParserService;
        this.documentChunkService = documentChunkService;
        this.ossService = ossService;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
        this.aiModelRegistry = aiModelRegistry;
    }

    public Mono<String> parseDocumentFromOss(KnowledgeDocument document, String parseMode) {
        return ossService.getFileInputStream(document.getOssPath())
                .flatMap(inputStream -> {
                    if (DocumentProcessingConstants.ParseMode.AI_ENHANCED.equals(parseMode)) {
                        return documentParserService.parseDocumentWithAI(
                                inputStream,
                                document.getFileName(),
                                document.getFileType(),
                                aiModelRegistry.getDefaultChatModel());
                    }
                    return documentParserService.parseDocument(
                            inputStream, document.getFileName(), document.getFileType());
                })
                .map(Document::text);
    }

    public Mono<Integer> createChunksAndIndex(DocumentProcessingMessage message, KnowledgeDocument document) {
        return documentChunkService.createDocumentChunks(
                        message.getDocumentId(), message.getKnowledgeBaseId(), document.getContent())
                .map(chunks -> chunks.size())
                .flatMap(chunkCount -> knowledgeChunkSearchService.indexDocumentChunks(message.getDocumentId())
                        .thenReturn(chunkCount));
    }
}
