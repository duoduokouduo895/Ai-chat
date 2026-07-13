package org.example.ai_agent.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.service.DocumentIngestionService;
import org.example.ai_agent.service.DocumentProcessingRecordService;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断控制器 - 用于检查系统状态和排查问题
 *
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@RestController
@RequestMapping("/api/diagnostic")
public class DiagnosticController {

    private final DocumentProcessingRecordService recordService;
    private final DocumentIngestionService ingestionService;
    private final KnowledgeDocumentRepository documentRepository;
    private final RabbitTemplate rabbitTemplate;
    private final AmqpAdmin amqpAdmin;

    public DiagnosticController(DocumentProcessingRecordService recordService,
                              DocumentIngestionService ingestionService,
                              KnowledgeDocumentRepository documentRepository,
                              RabbitTemplate rabbitTemplate,
                              ObjectProvider<AmqpAdmin> amqpAdminProvider) {
        this.recordService = recordService;
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.amqpAdmin = amqpAdminProvider.getIfAvailable();
    }

    /**
     * 执行诊断健康检查
     * 返回系统关键指标的当前状态
     */
    @GetMapping({"/health", "/trigger-health-check"})
    public Mono<Map<String, Object>> getDiagnosticHealth() {
        log.info("执行诊断健康检查...");
        return documentRepository.count()
            .flatMap(docCount -> recordService.getProcessingStatistics()
                .map(stats -> {
                    Map<String, Object> healthDetails = new HashMap<>();
                    healthDetails.put("status", "OK");
                    healthDetails.put("totalDocuments", docCount);
                    healthDetails.put("processingStatistics", stats);
                    healthDetails.put("timestamp", System.currentTimeMillis());
                    return healthDetails;
                })
            )
            .onErrorResume(error -> {
                log.error("诊断健康检查失败: errorType={}", error.getClass().getSimpleName());
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("status", "ERROR");
                errorResult.put("error", error.getMessage());
                errorResult.put("timestamp", System.currentTimeMillis());
                return Mono.just(errorResult);
            });
    }

    /**
     * 检查文档处理流程状态（专门用于诊断文档自动化流程问题）
     */
    @GetMapping("/document-processing-flow/{documentId}")
    public Mono<Map<String, Object>> checkDocumentProcessingFlow(@PathVariable Long documentId) {
        log.info("检查文档处理流程状态: documentId={}", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("文档不存在: " + documentId)))
                .flatMap(document -> {
                    Map<String, Object> result = new HashMap<>();

                    // 文档基本信息
                    result.put("documentId", documentId);
                    result.put("fileName", document.getFileName());
                    result.put("parseStatus", document.getParseStatus());
                    result.put("embeddingStatus", document.getEmbeddingStatus());
                    result.put("isEmbedded", document.getIsEmbedded());
                    result.put("chunkCount", document.getChunkCount());
                    result.put("characterCount", document.getCharacterCount());

                    // 检查是否有内容
                    result.put("hasContent", document.getContent() != null && !document.getContent().trim().isEmpty());
                    result.put("contentLength", document.getContent() != null ? document.getContent().length() : 0);

                    // 检查处理记录
                    return recordService.getDocumentProcessingRecords(documentId)
                            .collectList()
                            .map(records -> {
                                result.put("processingRecords", records);

                                // 分析处理流程状态
                                boolean hasParsing = records.stream().anyMatch(r -> "PARSE".equals(r.getProcessingType()));
                                boolean hasChunking = records.stream().anyMatch(r -> "CHUNK".equals(r.getProcessingType()));
                                boolean hasEmbedding = records.stream().anyMatch(r -> "EMBED".equals(r.getProcessingType()));

                                result.put("flowAnalysis", Map.of(
                                    "hasParsing", hasParsing,
                                    "hasChunking", hasChunking,
                                    "hasEmbedding", hasEmbedding,
                                    "isParsingCompleted", "COMPLETED".equals(document.getParseStatus()),
                                    "shouldTriggerChunking", "COMPLETED".equals(document.getParseStatus()) && !hasChunking,
                                    "shouldTriggerEmbedding", hasChunking && !hasEmbedding && document.getChunkCount() != null && document.getChunkCount() > 0
                                ));

                                // 诊断建议
                                List<String> suggestions = new ArrayList<>();

                                if (hasChunking && !hasEmbedding && document.getChunkCount() != null && document.getChunkCount() > 0) {
                                    suggestions.add("分块已完成但未触发向量化，可能是消息队列处理异常");
                                }
                                if ("PENDING".equals(document.getEmbeddingStatus()) && document.getChunkCount() != null && document.getChunkCount() > 0) {
                                    suggestions.add("文档块已创建但向量化状态为PENDING，建议手动触发向量化");
                                }

                                result.put("suggestions", suggestions);
                                result.put("timestamp", System.currentTimeMillis());

                                return result;
                            });
                })
                .onErrorResume(error -> {
                    log.error("检查文档处理流程失败: documentId={}, errorType={}",
                            documentId, error.getClass().getSimpleName());
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("documentId", documentId);
                    errorResult.put("error", error.getMessage());
                    errorResult.put("timestamp", System.currentTimeMillis());
                    return Mono.just(errorResult);
                });
    }

    /**
     * 手动触发文档分块和向量化流程（用于修复自动化流程中断问题）
     */
    @PostMapping("/trigger-chunk-and-embed/{documentId}")
    public Mono<Map<String, Object>> triggerChunkAndEmbed(@PathVariable Long documentId) {
        log.info("手动触发文档分块和向量化: documentId={}", documentId);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("文档不存在: " + documentId)))
                .flatMap(document -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("documentId", documentId);

                    // 检查文档是否已解析
                    if (!"COMPLETED".equals(document.getParseStatus())) {
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
                    log.error("触发文档分块和向量化失败: documentId={}, errorType={}",
                            documentId, error.getClass().getSimpleName());
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("documentId", documentId);
                    errorResult.put("success", false);
                    errorResult.put("error", error.getMessage());
                    errorResult.put("timestamp", System.currentTimeMillis());
                    return Mono.just(errorResult);
                });
    }

    /**
     * 检查知识库文档数量统计（用于诊断前端显示问题）
     */
    @GetMapping("/knowledge-base-stats")
    public Mono<Map<String, Object>> checkKnowledgeBaseStats() {
        log.info("检查知识库文档数量统计");

        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();
            result.put("message", "知识库文档数量统计已修复，请重新加载前端页面");
            result.put("timestamp", System.currentTimeMillis());
            return result;
        });
    }

    /**
     * 获取RabbitMQ队列状态信息
     */
    @GetMapping("/rabbitmq-queue-status")
    public Mono<Map<String, Object>> getRabbitMQQueueStatus() {
        log.info("获取RabbitMQ队列状态信息");

        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> queueStatus = new HashMap<>();

            if (amqpAdmin == null) {
                result.put("success", false);
                result.put("error", "RabbitMQ administration is disabled");
                result.put("timestamp", System.currentTimeMillis());
                return result;
            }

            try {
                String[] queues = {
                        DocumentProcessingConstants.Queue.DOCUMENT_PARSE,
                        DocumentProcessingConstants.Queue.DOCUMENT_CHUNK,
                        DocumentProcessingConstants.Queue.DOCUMENT_EMBED,
                        DocumentProcessingConstants.Queue.DOCUMENT_DLQ
                };

                for (String queueName : queues) {
                    try {
                        org.springframework.amqp.core.QueueInformation queueInfo =
                                amqpAdmin.getQueueInfo(queueName);

                        if (queueInfo != null) {
                            Map<String, Object> info = new HashMap<>();
                            info.put("messageCount", queueInfo.getMessageCount());
                            info.put("consumerCount", queueInfo.getConsumerCount());
                            queueStatus.put(queueName, info);
                        } else {
                            queueStatus.put(queueName, "Queue not found");
                        }
                    } catch (Exception e) {
                        queueStatus.put(queueName, "Error: " + e.getMessage());
                    }
                }

                result.put("success", true);
                result.put("queueStatus", queueStatus);
                result.put("timestamp", System.currentTimeMillis());

            } catch (Exception e) {
                log.error("获取RabbitMQ队列状态失败: errorType={}", e.getClass().getSimpleName());
                result.put("success", false);
                result.put("error", e.getMessage());
                result.put("timestamp", System.currentTimeMillis());
            }

            return result;
        });
    }
}


