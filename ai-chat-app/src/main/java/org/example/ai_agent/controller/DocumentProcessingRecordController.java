package org.example.ai_agent.controller;

import org.example.ai_agent.controller.support.KnowledgeAccessContextResolver;
import org.example.ai_agent.entity.DocumentProcessingRecord;
import org.example.ai_agent.service.DocumentProcessingRecordService;
import org.example.ai_agent.service.DocumentService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** HTTP seam for document-processing record inspection and operational statistics. */
@RestController
@RequestMapping("/api/knowledge")
public class DocumentProcessingRecordController {

    private final DocumentService documentService;
    private final DocumentProcessingRecordService recordService;
    private final KnowledgeAccessContextResolver accessContextResolver;

    public DocumentProcessingRecordController(DocumentService documentService,
                                              DocumentProcessingRecordService recordService,
                                              KnowledgeAccessContextResolver accessContextResolver) {
        this.documentService = documentService;
        this.recordService = recordService;
        this.accessContextResolver = accessContextResolver;
    }

    @GetMapping("/documents/{id}/processing-records")
    public Flux<DocumentProcessingRecord> getDocumentProcessingRecords(@PathVariable Long id,
                                                                        Authentication authentication) {
        return accessContextResolver.resolve(authentication)
                .flatMapMany(access -> documentService.getDocument(id, access.userId(), access.isAdmin())
                        .thenMany(recordService.getDocumentProcessingRecords(id)));
    }

    @GetMapping("/processing-records/failed")
    public Flux<DocumentProcessingRecord> getFailedProcessingRecords(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            Authentication authentication) {
        return accessContextResolver.requireAdmin(authentication)
                .thenMany(recordService.getFailedProcessingRecords(page, size));
    }

    @GetMapping("/processing-statistics")
    public Mono<DocumentProcessingRecordService.ProcessingStatistics> getProcessingStatistics(
            Authentication authentication) {
        return accessContextResolver.requireAdmin(authentication)
                .then(recordService.getProcessingStatistics());
    }
}
