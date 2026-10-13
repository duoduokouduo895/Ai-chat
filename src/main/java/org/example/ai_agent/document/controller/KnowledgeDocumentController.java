package org.example.ai_agent.document.controller;

import org.example.ai_agent.knowledge.controller.KnowledgeAccessContextResolver;
import org.example.ai_agent.admin.dto.AdminPageDto;
import org.example.ai_agent.document.dto.DocumentBatchUploadDto;
import org.example.ai_agent.document.dto.DocumentDto;
import org.example.ai_agent.document.service.DocumentChunkService;
import org.example.ai_agent.document.service.DocumentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/** HTTP seam for document upload, access, lifecycle operations and diagnostics. */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeDocumentController {
    private final org.example.ai_agent.knowledge.cleanup.KnowledgeCleanupCommands cleanup;

    private final DocumentService documentService;
    private final DocumentChunkService documentChunkService;
    private final KnowledgeAccessContextResolver accessContextResolver;
    private final KnowledgeDocumentUploadFacade uploadFacade;

    public KnowledgeDocumentController(DocumentService documentService,
                                       DocumentChunkService documentChunkService,
                                       KnowledgeAccessContextResolver accessContextResolver,
                                       KnowledgeDocumentUploadFacade uploadFacade, org.example.ai_agent.knowledge.cleanup.KnowledgeCleanupCommands cleanup) {
        this.documentService = documentService;
        this.documentChunkService = documentChunkService;
        this.accessContextResolver = accessContextResolver;
        this.uploadFacade = uploadFacade;
        this.cleanup = cleanup;
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentDto> uploadDocumentDirect(ServerWebExchange exchange, Authentication authentication) {
        return uploadFacade.uploadDocumentDirect(exchange, authentication);
    }

    @PostMapping(value = "/admin/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentDto> uploadDocumentDirectForAdmin(ServerWebExchange exchange, Authentication authentication) {
        return uploadFacade.uploadDocumentDirectForAdmin(exchange, authentication);
    }

    @PostMapping("/bases/{knowledgeBaseId}/documents")
    public Mono<DocumentDto> uploadDocument(@PathVariable Long knowledgeBaseId,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "parseMode", defaultValue = "auto") String parseMode,
                                            @RequestParam(value = "enableVectorization", defaultValue = "true") Boolean enableVectorization,
                                            Authentication authentication) {
        return uploadFacade.uploadDocument(knowledgeBaseId, file, parseMode, enableVectorization, authentication);
    }

    @PostMapping("/bases/{knowledgeBaseId}/documents/batch")
    public Mono<DocumentBatchUploadDto> batchUploadDocuments(@PathVariable Long knowledgeBaseId,
                                                              @RequestParam("files") MultipartFile[] files,
                                                              @RequestParam(value = "parseMode", defaultValue = "auto") String parseMode,
                                                              @RequestParam(value = "enableVectorization", defaultValue = "true") Boolean enableVectorization,
                                                              Authentication authentication) {
        return uploadFacade.batchUploadDocuments(knowledgeBaseId, files, parseMode, enableVectorization, authentication);
    }

    @GetMapping("/bases/{knowledgeBaseId}/documents")
    public Flux<DocumentDto> getDocuments(@PathVariable Long knowledgeBaseId,
                                          @RequestParam(defaultValue = "0") Integer page,
                                          @RequestParam(defaultValue = "10") Integer size,
                                          Authentication authentication) {
        return accessContextResolver.resolve(authentication)
                .flatMapMany(access -> documentService.getDocuments(
                        knowledgeBaseId, page, size, access.userId(), access.isAdmin()));
    }

    @GetMapping("/admin/documents")
    public Flux<DocumentDto> getAllDocumentsForAdmin(@RequestParam(defaultValue = "0") Integer page,
                                                      @RequestParam(defaultValue = "20") Integer size) {
        return documentService.getAllDocuments(page, size);
    }

    @GetMapping("/admin/documents/page")
    public Mono<AdminPageDto<DocumentDto>> getAllDocumentsPageForAdmin(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String parseStatus,
            @RequestParam(required = false) String embeddingStatus) {
        return documentService.getAllDocumentsPage(page, size, keyword, parseStatus, embeddingStatus);
    }

    @GetMapping("/documents/{id}")
    public Mono<DocumentDto> getDocument(@PathVariable Long id, Authentication authentication) {
        return accessContextResolver.resolve(authentication)
                .flatMap(access -> documentService.getDocument(id, access.userId(), access.isAdmin()));
    }

    @DeleteMapping("/documents/{id}")
    public Mono<org.springframework.http.ResponseEntity<org.example.ai_agent.document.domain.CleanupOperation>> deleteDocument(@PathVariable Long id, Authentication authentication) {
        return accessContextResolver.resolve(authentication)
                .flatMap(access -> cleanup.deleteDocument(id, access.userId(), access.isAdmin()))
                .map(operation -> org.springframework.http.ResponseEntity.accepted().body(operation));
    }

    @GetMapping("/documents/{id}/download")
    public Mono<ResponseEntity<Object>> downloadDocument(@PathVariable Long id, Authentication authentication) {
        return getDocument(id, authentication)
                .flatMap(document -> {
                    if (document.getOssUrl() == null) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("Location", document.getOssUrl());
                    headers.add("Content-Disposition", "attachment; filename=\"" + document.getFileName() + "\"");
                    return Mono.just(ResponseEntity.status(HttpStatus.FOUND).headers(headers).build());
                });
    }

    @PostMapping("/documents/{id}/reparse")
    public Mono<DocumentDto> reparseDocument(@PathVariable Long id, Authentication authentication) {
        return accessContextResolver.resolve(authentication)
                .flatMap(access -> documentService.reparseDocument(id, access.userId(), access.isAdmin()));
    }

    @PostMapping("/documents/{id}/re-embed")
    public Mono<DocumentDto> reEmbedDocument(@PathVariable Long id, Authentication authentication) {
        return accessContextResolver.resolve(authentication)
                .flatMap(access -> documentService.reEmbedDocument(id, access.userId(), access.isAdmin()));
    }

    @GetMapping("/documents/{id}/embedding-status")
    public Mono<Map<String, Object>> checkDocumentEmbeddingStatus(@PathVariable Long id,
                                                                    Authentication authentication) {
        return getDocument(id, authentication)
                .flatMap(document -> documentChunkService.getDocumentChunks(id)
                        .collectList()
                        .map(chunks -> createEmbeddingStatus(id, document, chunks)));
    }

    private Map<String, Object> createEmbeddingStatus(Long documentId,
                                                       DocumentDto document,
                                                       java.util.List<org.example.ai_agent.document.dto.DocumentChunkDto> chunks) {
        Map<String, Object> status = new HashMap<>();
        status.put("documentId", documentId);
        status.put("fileName", document.getFileName());
        status.put("parseStatus", document.getParseStatus());
        status.put("embeddingStatus", document.getEmbeddingStatus());
        status.put("isEmbedded", document.getIsEmbedded());
        status.put("chunkCount", document.getChunkCount());
        status.put("characterCount", document.getCharacterCount());

        long embeddedChunks = chunks.stream().filter(chunk -> Boolean.TRUE.equals(chunk.getIsEmbedded())).count();
        status.put("totalChunks", chunks.size());
        status.put("embeddedChunks", embeddedChunks);
        status.put("embeddingProgress", chunks.isEmpty() ? 0D : (double) embeddedChunks / chunks.size() * 100);
        status.put("chunkStatusCounts", chunks.stream().collect(java.util.stream.Collectors.groupingBy(
                chunk -> chunk.getEmbeddingStatus() == null ? "NULL" : chunk.getEmbeddingStatus(),
                java.util.stream.Collectors.counting())));
        boolean actuallyEmbedded = embeddedChunks == chunks.size() && !chunks.isEmpty();
        status.put("shouldBeEmbedded", actuallyEmbedded);
        status.put("statusMismatch", actuallyEmbedded != Boolean.TRUE.equals(document.getIsEmbedded()));
        status.put("timestamp", System.currentTimeMillis());
        return status;
    }
}
