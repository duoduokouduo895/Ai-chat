package org.example.ai_agent.document.service;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.document.domain.DocumentProcessingConstants;
import org.example.ai_agent.document.domain.DocumentProcessingStatus;
import org.example.ai_agent.knowledge.repository.KnowledgeDocumentRepository;
import org.example.ai_agent.document.pipeline.DocumentIngestionService;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DocumentProcessingDiagnosticQueryImpl implements DocumentProcessingDiagnosticQuery {

    private final DocumentProcessingRecordService recordService;
    private final DocumentIngestionService ingestionService;
    private final KnowledgeDocumentRepository documentRepository;
    private final AmqpAdmin amqpAdmin;

    public DocumentProcessingDiagnosticQueryImpl(DocumentProcessingRecordService recordService,
                                                 DocumentIngestionService ingestionService,
                                                 KnowledgeDocumentRepository documentRepository,
                                                 ObjectProvider<AmqpAdmin> amqpAdminProvider) {
        this.recordService = recordService;
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
        this.amqpAdmin = amqpAdminProvider.getIfAvailable();
    }

    @Override
    public Mono<Map<String, Object>> getHealth() {
        return documentRepository.count()
                .flatMap(docCount -> recordService.getProcessingStatistics()
                        .map(stats -> {
                            Map<String, Object> healthDetails = new HashMap<>();
                            healthDetails.put("status", "OK");
                            healthDetails.put("totalDocuments", docCount);
                            healthDetails.put("processingStatistics", stats);
                            healthDetails.put("timestamp", System.currentTimeMillis());
                            return healthDetails;
                        }))
                .onErrorResume(error -> {
                    log.error("诊断健康检查失败: errorType={}", error.getClass().getSimpleName());
                    return Mono.just(errorResult(error, null, null, true));
                });
    }

    @Override
    public Mono<Map<String, Object>> getDocumentProcessingFlow(Long documentId) {
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("文档不存在: " + documentId)))
                .flatMap(document -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("documentId", documentId);
                    result.put("fileName", document.getFileName());
                    result.put("parseStatus", document.getParseStatus());
                    result.put("embeddingStatus", document.getEmbeddingStatus());
                    result.put("isEmbedded", document.getIsEmbedded());
                    result.put("chunkCount", document.getChunkCount());
                    result.put("characterCount", document.getCharacterCount());
                    result.put("hasContent", document.getContent() != null && !document.getContent().trim().isEmpty());
                    result.put("contentLength", document.getContent() != null ? document.getContent().length() : 0);
                    return recordService.getDocumentProcessingRecords(documentId)
                            .collectList()
                            .map(records -> {
                                result.put("processingRecords", records);
                                boolean hasParsing = records.stream().anyMatch(r -> "PARSE".equals(r.getProcessingType()));
                                boolean hasChunking = records.stream().anyMatch(r -> "CHUNK".equals(r.getProcessingType()));
                                boolean hasEmbedding = records.stream().anyMatch(r -> "EMBED".equals(r.getProcessingType()));
                                result.put("flowAnalysis", Map.of(
                                        "hasParsing", hasParsing,
                                        "hasChunking", hasChunking,
                                        "hasEmbedding", hasEmbedding,
                                        "isParsingCompleted", DocumentProcessingStatus.COMPLETED.value().equals(document.getParseStatus()),
                                        "shouldTriggerChunking", DocumentProcessingStatus.COMPLETED.value().equals(document.getParseStatus()) && !hasChunking,
                                        "shouldTriggerEmbedding", hasChunking && !hasEmbedding
                                                && document.getChunkCount() != null && document.getChunkCount() > 0));
                                List<String> suggestions = new ArrayList<>();
                                if (hasChunking && !hasEmbedding && document.getChunkCount() != null && document.getChunkCount() > 0) {
                                    suggestions.add("分块已完成但未触发向量化，可能是消息队列处理异常");
                                }
                                if (DocumentProcessingStatus.PENDING.value().equals(document.getEmbeddingStatus())
                                        && document.getChunkCount() != null && document.getChunkCount() > 0) {
                                    suggestions.add("文档块已创建但向量化状态为PENDING，建议手动触发向量化");
                                }
                                result.put("suggestions", suggestions);
                                result.put("timestamp", System.currentTimeMillis());
                                return result;
                            });
                })
                .onErrorResume(error -> {
                    log.error("检查文档处理流程失败: documentId={}, errorType={}", documentId,
                            error.getClass().getSimpleName());
                    return Mono.just(errorResult(error, documentId, null, false));
                });
    }

    @Override
    public Mono<Map<String, Object>> resumeChunkAndEmbed(Long documentId) {
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("文档不存在: " + documentId)))
                .flatMap(document -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("documentId", documentId);
                    if (!DocumentProcessingStatus.COMPLETED.value().equals(document.getParseStatus())) {
                        result.put("success", false);
                        result.put("error", "文档尚未解析完成，无法进行分块和向量化");
                        return Mono.just(result);
                    }
                    if (document.getContent() == null || document.getContent().trim().isEmpty()) {
                        result.put("success", false);
                        result.put("error", "文档内容为空，无法进行分块和向量化");
                        return Mono.just(result);
                    }
                    return ingestionService.resumeChunkAndEmbed(documentId, document.getKnowledgeBaseId())
                            .map(task -> {
                                result.put("success", true);
                                result.put("message", "文档 ingestion 已从分块阶段恢复，将继续自动触发向量化流程");
                                result.put("chunkMessageSent", true);
                                result.put("action", task.action());
                                result.put("firstStage", task.firstStage());
                                result.put("timestamp", System.currentTimeMillis());
                                return result;
                            });
                })
                .onErrorResume(error -> {
                    log.error("触发文档分块和向量化失败: documentId={}, errorType={}", documentId,
                            error.getClass().getSimpleName());
                    return Mono.just(errorResult(error, documentId, false, false));
                });
    }

    @Override
    public Mono<Map<String, Object>> getRabbitQueueStatus() {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();
            if (amqpAdmin == null) {
                result.put("success", false);
                result.put("error", "RabbitMQ administration is disabled");
                result.put("timestamp", System.currentTimeMillis());
                return result;
            }
            Map<String, Object> queueStatus = new HashMap<>();
            String[] queues = {DocumentProcessingConstants.Queue.DOCUMENT_PARSE,
                    DocumentProcessingConstants.Queue.DOCUMENT_CHUNK,
                    DocumentProcessingConstants.Queue.DOCUMENT_EMBED,
                    DocumentProcessingConstants.Queue.DOCUMENT_DLQ};
            for (String queueName : queues) {
                try {
                    org.springframework.amqp.core.QueueInformation queueInfo = amqpAdmin.getQueueInfo(queueName);
                    if (queueInfo == null) {
                        queueStatus.put(queueName, "Queue not found");
                    } else {
                        queueStatus.put(queueName, Map.of("messageCount", queueInfo.getMessageCount(),
                                "consumerCount", queueInfo.getConsumerCount()));
                    }
                } catch (Exception error) {
                    queueStatus.put(queueName, "Error: " + error.getMessage());
                }
            }
            result.put("success", true);
            result.put("queueStatus", queueStatus);
            result.put("timestamp", System.currentTimeMillis());
            return result;
        }).onErrorResume(error -> {
            log.error("获取RabbitMQ队列状态失败: errorType={}", error.getClass().getSimpleName());
            return Mono.just(errorResult(error, null, false, false));
        });
    }

    private Map<String, Object> errorResult(Throwable error, Long documentId, Boolean success, boolean includeStatus) {
        Map<String, Object> errorResult = new HashMap<>();
        if (documentId != null) {
            errorResult.put("documentId", documentId);
        }
        if (success != null) {
            errorResult.put("success", success);
        }
        if (includeStatus) {
            errorResult.put("status", "ERROR");
        }
        errorResult.put("error", error.getMessage());
        errorResult.put("timestamp", System.currentTimeMillis());
        return errorResult;
    }
}
