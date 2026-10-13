package org.example.ai_agent.document.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.document.domain.DocumentProcessingConstants;
import org.example.ai_agent.document.dto.DocumentIngestionTaskDto;
import org.example.ai_agent.document.dto.DocumentProcessingMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 文档 ingestion 门面实现。
 * 负责把外部动作收敛成统一的入库流水线入口，底层将第一阶段消息写入 transactional outbox。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private final DocumentProcessingService documentProcessingService;

    @Override
    public Mono<DocumentIngestionTaskDto> startIngestion(Long documentId, Long knowledgeBaseId,
                                                         String parseMode, Boolean enableVectorization) {
        log.info("受理文档 ingestion 请求: documentId={}, knowledgeBaseId={}, parseMode={}, enableVectorization={}",
                documentId, knowledgeBaseId, parseMode, enableVectorization);

        return documentProcessingService.startDocumentProcessing(
                        documentId, knowledgeBaseId, parseMode, enableVectorization)
                .flatMap(started -> started
                        ? Mono.just(buildAcceptedTask(
                                documentId, knowledgeBaseId, "START_INGESTION", "PARSE",
                                parseMode, enableVectorization))
                        : Mono.error(new IllegalStateException("文档 ingestion 流水线启动失败")));
    }

    @Override
    public Mono<DocumentIngestionTaskDto> reparseDocument(Long documentId, Long knowledgeBaseId, String parseMode) {
        log.info("受理文档重新解析请求: documentId={}, knowledgeBaseId={}, parseMode={}",
                documentId, knowledgeBaseId, parseMode);

        return documentProcessingService.reparseDocument(documentId, parseMode)
                .flatMap(started -> started
                        ? Mono.just(buildAcceptedTask(
                                documentId, knowledgeBaseId, "REPARSE_DOCUMENT", "PARSE",
                                parseMode, true))
                        : Mono.error(new IllegalStateException("重新解析文档启动失败")));
    }

    @Override
    public Mono<DocumentIngestionTaskDto> reEmbedDocument(Long documentId, Long knowledgeBaseId) {
        log.info("受理文档重新向量化请求: documentId={}, knowledgeBaseId={}", documentId, knowledgeBaseId);

        return documentProcessingService.reEmbedDocument(documentId)
                .flatMap(started -> started
                        ? Mono.just(buildAcceptedTask(
                                documentId, knowledgeBaseId, "RE_EMBED_DOCUMENT", "EMBED",
                                null, true))
                        : Mono.error(new IllegalStateException("重新向量化文档启动失败")));
    }

    @Override
    public Mono<DocumentIngestionTaskDto> resumeChunkAndEmbed(Long documentId, Long knowledgeBaseId) {
        log.info("受理文档补触发分块与向量化请求: documentId={}, knowledgeBaseId={}", documentId, knowledgeBaseId);

        DocumentProcessingMessage message = DocumentProcessingMessage.createChunkMessage(
                documentId, knowledgeBaseId, true);

        return documentProcessingService.sendProcessingMessage(
                        message, DocumentProcessingConstants.RoutingKey.DOCUMENT_CHUNK)
                .flatMap(sent -> sent
                        ? Mono.just(buildAcceptedTask(
                                documentId, knowledgeBaseId, "RESUME_CHUNK_AND_EMBED", "CHUNK",
                                null, true))
                        : Mono.error(new IllegalStateException("补触发分块与向量化失败")));
    }

    private DocumentIngestionTaskDto buildAcceptedTask(Long documentId, Long knowledgeBaseId,
                                                       String action, String firstStage,
                                                       String parseMode, Boolean enableVectorization) {
        return new DocumentIngestionTaskDto(
                documentId,
                knowledgeBaseId,
                action,
                firstStage,
                parseMode,
                enableVectorization,
                LocalDateTime.now()
        );
    }
}
