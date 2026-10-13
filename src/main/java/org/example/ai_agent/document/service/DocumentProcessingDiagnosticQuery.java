package org.example.ai_agent.document.service;

import reactor.core.publisher.Mono;

import java.util.Map;

/** Application interface used by the diagnostic HTTP adapter. */
public interface DocumentProcessingDiagnosticQuery {

    Mono<Map<String, Object>> getHealth();

    Mono<Map<String, Object>> getDocumentProcessingFlow(Long documentId);

    Mono<Map<String, Object>> resumeChunkAndEmbed(Long documentId);

    Mono<Map<String, Object>> getRabbitQueueStatus();
}
