package org.example.ai_agent.document.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.document.service.DocumentProcessingDiagnosticQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/** HTTP adapter for document-processing operational diagnostics. */
@Slf4j
@RestController
@RequestMapping("/api/diagnostic")
public class DiagnosticController {

    private final DocumentProcessingDiagnosticQuery diagnosticQuery;

    public DiagnosticController(DocumentProcessingDiagnosticQuery diagnosticQuery) {
        this.diagnosticQuery = diagnosticQuery;
    }

    @GetMapping({"/health", "/trigger-health-check"})
    public Mono<Map<String, Object>> getDiagnosticHealth() {
        log.info("执行诊断健康检查");
        return diagnosticQuery.getHealth();
    }

    @GetMapping("/document-processing-flow/{documentId}")
    public Mono<Map<String, Object>> checkDocumentProcessingFlow(@PathVariable Long documentId) {
        log.info("检查文档处理流程状态: documentId={}", documentId);
        return diagnosticQuery.getDocumentProcessingFlow(documentId);
    }

    @PostMapping("/trigger-chunk-and-embed/{documentId}")
    public Mono<Map<String, Object>> triggerChunkAndEmbed(@PathVariable Long documentId) {
        log.info("手动触发文档分块和向量化: documentId={}", documentId);
        return diagnosticQuery.resumeChunkAndEmbed(documentId);
    }

    @GetMapping("/knowledge-base-stats")
    public Mono<Map<String, Object>> checkKnowledgeBaseStats() {
        return Mono.fromSupplier(() -> Map.of(
                "message", "知识库文档数量统计已修复，请重新加载前端页面",
                "timestamp", System.currentTimeMillis()));
    }

    @GetMapping("/rabbitmq-queue-status")
    public Mono<Map<String, Object>> getRabbitMQQueueStatus() {
        log.info("获取RabbitMQ队列状态信息");
        return diagnosticQuery.getRabbitQueueStatus();
    }
}
