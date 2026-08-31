package org.example.ai_agent.service;

import org.example.ai_agent.dto.document.DocumentIngestionTaskDto;
import reactor.core.publisher.Mono;

/**
 * 文档 ingestion 门面服务。
 * 对外统一表达文档入库主线：parse -> chunk -> embed -> store。
 */
public interface DocumentIngestionService {

    Mono<DocumentIngestionTaskDto> startIngestion(Long documentId, Long knowledgeBaseId,
                                                  String parseMode, Boolean enableVectorization);

    Mono<DocumentIngestionTaskDto> reparseDocument(Long documentId, Long knowledgeBaseId, String parseMode);

    Mono<DocumentIngestionTaskDto> reEmbedDocument(Long documentId, Long knowledgeBaseId);

    Mono<DocumentIngestionTaskDto> resumeChunkAndEmbed(Long documentId, Long knowledgeBaseId);
}
