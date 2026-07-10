package org.example.ai_agent.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.admin.AdminPageDto;
import org.example.ai_agent.dto.document.DocumentBatchUploadDto;
import org.example.ai_agent.dto.document.DocumentDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseCreateDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseStatsDto;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchDto;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchResultDto;
import org.example.ai_agent.controller.support.KnowledgeAccessContextResolver;
import org.example.ai_agent.controller.support.KnowledgeDocumentUploadFacade;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.DocumentProcessingRecordService;
import org.example.ai_agent.service.DocumentChunkService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.KnowledgeChunkSearchService;
import org.example.ai_agent.service.RagService;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 知识库控制器。
 * 对外主要承载知识库管理、文档入库、检索等能力。
 * 其中旧的 RAG 专用聊天接口仅保留为兼容入口，主聊天链路已经收敛到统一 Assistant。
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final RagService ragService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentService documentService;
    private final DocumentProcessingRecordService recordService;
    private final DocumentChunkService documentChunkService;
    private final KnowledgeChunkSearchService knowledgeChunkSearchService;
    private final KnowledgeAccessContextResolver accessContextResolver;
    private final KnowledgeDocumentUploadFacade uploadFacade;

    public KnowledgeController(RagService ragService,
                               KnowledgeBaseService knowledgeBaseService,
                               DocumentService documentService,
                               DocumentProcessingRecordService recordService,
                               DocumentChunkService documentChunkService,
                               KnowledgeChunkSearchService knowledgeChunkSearchService,
                               KnowledgeAccessContextResolver accessContextResolver,
                               KnowledgeDocumentUploadFacade uploadFacade) {
        this.ragService = ragService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentService = documentService;
        this.recordService = recordService;
        this.documentChunkService = documentChunkService;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
        this.accessContextResolver = accessContextResolver;
        this.uploadFacade = uploadFacade;
    }
    /**
     * 搜索知识库
     */
    @PostMapping("/search")
    public Mono<KnowledgeSearchResultDto> searchKnowledge(
            @Valid @RequestBody KnowledgeSearchDto searchRequest,
            Authentication authentication) {

        log.info("知识库搜索请求: query={}, knowledgeBaseIds={}, maxResults={}",
                searchRequest.getQuery(), searchRequest.getKnowledgeBaseIds(), searchRequest.getMaxResults());

        return accessContextResolver.resolve(authentication)
                .flatMap(access -> ragService.searchKnowledge(searchRequest, access.userId(), access.isAdmin()))
                .doOnSuccess(result -> log.info("知识库搜索完成: resultCount={}, searchTime={}ms",
                        result.getTotalCount(), result.getSearchTime()))
                .doOnError(error -> log.error("知识库搜索失败: query={}, error={}",
                        searchRequest.getQuery(), error.getMessage()));
    }

    /** 旧 RAG 生成入口已关闭，避免绕过统一聊天链路的额度与并发控制。 */
    @Deprecated
    @PostMapping("/chat")
    public Mono<ResponseEntity<Map<String, Object>>> ragChat() {
        return legacyRagEndpointClosed();
    }

    /** 旧 RAG 流式入口已关闭，调用方应改用 /api/ai/chat 的 SSE 协议。 */
    @Deprecated
    @PostMapping("/chat/stream")
    public Mono<ResponseEntity<Map<String, Object>>> ragChatStream() {
        return legacyRagEndpointClosed();
    }

    private Mono<ResponseEntity<Map<String, Object>>> legacyRagEndpointClosed() {
        Map<String, Object> body = Map.of(
                "code", HttpStatus.GONE.value(),
                "errorCode", "LEGACY_RAG_ENDPOINT_RETIRED",
                "message", "该接口已停用，请改用 /api/ai/chat",
                "replacement", "/api/ai/chat"
        );
        return Mono.just(ResponseEntity.status(HttpStatus.GONE)
                .header("Deprecation", "true")
                .header(HttpHeaders.LINK, "</api/ai/chat>; rel=\"successor-version\"")
                .body(body));
    }

    /**
     * 创建知识库
     */
    @PostMapping("/bases")
    public Mono<KnowledgeBaseDto> createKnowledgeBase(@Valid @RequestBody KnowledgeBaseCreateDto createRequest,
                                                      Authentication authentication) {
        log.info("创建知识库请求: name={}, description={}, isPublic={}, embeddingModel={}",
                createRequest.getName(), createRequest.getDescription(),
                createRequest.getIsPublic(), createRequest.getEmbeddingModel());

        return accessContextResolver.requireUser(authentication)
                .flatMap(access -> knowledgeBaseService.createKnowledgeBase(createRequest, access.userId())
                        .doOnSuccess(dto -> log.info("知识库创建成功: id={}, name={}, creatorId={}",
                                dto.getId(), dto.getName(), access.userId()))
                        .doOnError(error -> log.error("知识库创建失败: name={}, error={}",
                                createRequest.getName(), error.getMessage())));
    }

    /**
     * 管理员创建知识库
     */
    @PostMapping("/admin/bases")
    public Mono<KnowledgeBaseDto> createKnowledgeBaseForAdmin(@Valid @RequestBody KnowledgeBaseCreateDto createRequest,
                                                              @RequestParam Long creatorId,
                                                              Authentication authentication) {
        log.info("管理员创建知识库请求: name={}, creatorId={}, isPublic={}",
                createRequest.getName(), creatorId, createRequest.getIsPublic());

        return accessContextResolver.requireAdmin(authentication)
                .then(accessContextResolver.requireExistingUserId(creatorId, "知识库归属用户不存在"))
                .flatMap(validCreatorId -> knowledgeBaseService.createKnowledgeBase(createRequest, validCreatorId))
                .doOnSuccess(dto -> log.info("管理员创建知识库成功: id={}, name={}, creatorId={}",
                        dto.getId(), dto.getName(), creatorId))
                .doOnError(error -> log.error("管理员创建知识库失败: creatorId={}, error={}",
                        creatorId, error.getMessage()));
    }

    /**
     * 获取知识库列表
     */
    @GetMapping("/bases")
    public Flux<KnowledgeBaseDto> getKnowledgeBases(
            @RequestParam(required = false) Boolean publicOnly,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            Authentication authentication) {
        log.info("查询知识库列表: publicOnly={}, page={}, size={}", publicOnly, page, size);

        return accessContextResolver.resolve(authentication)
                .flatMapMany(access -> knowledgeBaseService.getKnowledgeBases(
                        access.isAdmin() ? null : access.userId(), publicOnly, page, size))
                .doOnComplete(() -> log.info("知识库列表查询完成"))
                .doOnError(error -> log.error("知识库列表查询失败: error={}", error.getMessage()));
    }

    /**
     * 管理员视角获取全站知识库列表
     */
    @GetMapping("/admin/bases")
    public Flux<KnowledgeBaseDto> getAllKnowledgeBasesForAdmin(@RequestParam(defaultValue = "0") Integer page,
                                                               @RequestParam(defaultValue = "100") Integer size) {
        log.info("管理员查询全站知识库列表: page={}, size={}", page, size);

        return knowledgeBaseService.getAllKnowledgeBases(page, size)
                .doOnComplete(() -> log.info("管理员全站知识库列表查询完成"))
                .doOnError(error -> log.error("管理员全站知识库列表查询失败: error={}", error.getMessage()));
    }

    /**
     * 管理员视角分页查询全站知识库，包含真实聚合统计。
     */
    @GetMapping("/admin/bases/page")
    public Mono<AdminPageDto<KnowledgeBaseDto>> getAllKnowledgeBasesPageForAdmin(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        return knowledgeBaseService.getAllKnowledgeBasesPage(page, size, keyword);
    }

    /**
     * 管理员重建知识库 BM25 文档块索引。
     */
    @PostMapping("/admin/chunk-search/rebuild")
    public Mono<Void> rebuildKnowledgeChunkSearchIndex(Authentication authentication) {
        return accessContextResolver.requireAdmin(authentication)
                .then(knowledgeChunkSearchService.rebuildAllChunks())
                .doOnSuccess(unused -> log.info("知识库 BM25 文档块索引重建完成"))
                .doOnError(error -> log.error("知识库 BM25 文档块索引重建失败: error={}", error.getMessage(), error));
    }

    /**
     * 获取知识库详情
     */
    @GetMapping("/bases/{id}")
    public Mono<KnowledgeBaseDto> getKnowledgeBase(@PathVariable Long id, Authentication authentication) {
        log.info("查询知识库详情: id={}", id);

        return accessContextResolver.resolve(authentication)
                .flatMap(access -> knowledgeBaseService.getKnowledgeBase(id, access.userId(), access.isAdmin()))
                .doOnSuccess(dto -> log.info("知识库详情查询成功: id={}, name={}", id, dto.getName()))
                .doOnError(error -> log.error("知识库详情查询失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 获取知识库聚合统计。
     */
    @GetMapping("/bases/{id}/stats")
    public Mono<KnowledgeBaseStatsDto> getKnowledgeBaseStats(@PathVariable Long id,
                                                             Authentication authentication) {
        log.info("查询知识库统计: id={}", id);

        return accessContextResolver.resolve(authentication)
                .flatMap(access -> knowledgeBaseService.getKnowledgeBaseStats(id, access.userId(), access.isAdmin()))
                .doOnSuccess(stats -> log.info("知识库统计查询成功: id={}, documentCount={}",
                        id, stats.getDocumentCount()))
                .doOnError(error -> log.error("知识库统计查询失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 更新知识库
     */
    @PutMapping("/bases/{id}")
    public Mono<KnowledgeBaseDto> updateKnowledgeBase(@PathVariable Long id,
                                                      @Valid @RequestBody KnowledgeBaseCreateDto updateRequest,
                                                      Authentication authentication) {
        log.info("更新知识库请求: id={}, name={}", id, updateRequest.getName());

        return accessContextResolver.resolve(authentication)
                .flatMap(access -> knowledgeBaseService.updateKnowledgeBase(id, updateRequest, access.userId(), access.isAdmin()))
                .doOnSuccess(dto -> log.info("知识库更新成功: id={}, name={}", id, dto.getName()))
                .doOnError(error -> log.error("知识库更新失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/bases/{id}")
    public Mono<Void> deleteKnowledgeBase(@PathVariable Long id, Authentication authentication) {
        log.info("删除知识库请求: id={}", id);

        return accessContextResolver.resolve(authentication)
                .flatMap(access -> knowledgeBaseService.deleteKnowledgeBase(id, access.userId(), access.isAdmin()))
                .doOnSuccess(v -> log.info("知识库删除成功: id={}", id))
                .doOnError(error -> log.error("知识库删除失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 测试知识库是否存在
     */
    @GetMapping("/bases/{id}/exists")
    public Mono<Boolean> checkKnowledgeBaseExists(@PathVariable Long id, Authentication authentication) {
        log.info("检查知识库是否存在: id={}", id);

        return accessContextResolver.resolve(authentication)
                .flatMap(access -> knowledgeBaseService
                        .getAuthorizedKnowledgeBase(id, access.userId(), access.isAdmin(), false)
                        .thenReturn(Boolean.TRUE))
                .doOnSuccess(exists -> log.info("知识库存在检查结果: id={}, exists={}", id, exists))
                .doOnError(error -> log.error("知识库存在检查失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 上传文档（前端专用接口） - 兼容性更好的版本
     */
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentDto> uploadDocumentDirect(ServerWebExchange exchange, Authentication authentication) {
        return uploadFacade.uploadDocumentDirect(exchange, authentication)
                .doOnSuccess(dto -> log.info("文档上传成功: id={}, fileName={}", dto.getId(), dto.getFileName()))
                .doOnError(error -> log.error("文档上传失败: error={}", error.getMessage(), error));
    }

    /**
     * 管理员上传文档
     */
    @PostMapping(value = "/admin/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentDto> uploadDocumentDirectForAdmin(ServerWebExchange exchange, Authentication authentication) {
        return uploadFacade.uploadDocumentDirectForAdmin(exchange, authentication)
                .doOnSuccess(dto -> log.info("管理员文档上传成功: id={}, fileName={}, uploaderId={}",
                        dto.getId(), dto.getFileName(), dto.getUploaderId()))
                .doOnError(error -> log.error("管理员文档上传失败: error={}", error.getMessage(), error));
    }

    /**
     * 上传文档到知识库
     */
    @PostMapping("/bases/{knowledgeBaseId}/documents")
    public Mono<DocumentDto> uploadDocument(@PathVariable Long knowledgeBaseId,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "parseMode", defaultValue = "auto") String parseMode,
                                            @RequestParam(value = "enableVectorization", defaultValue = "true") Boolean enableVectorization,
                                            Authentication authentication) {

        log.info("知识库文档上传请求: knowledgeBaseId={}, fileName={}, parseMode={}, enableVectorization={}",
                knowledgeBaseId, file.getOriginalFilename(), parseMode, enableVectorization);

        return uploadFacade.uploadDocument(knowledgeBaseId, file, parseMode, enableVectorization, authentication)
                .doOnSuccess(dto -> log.info("知识库文档上传成功: id={}, fileName={}, uploaderId={}",
                        dto.getId(), dto.getFileName(), dto.getUploaderId()))
                .doOnError(error -> log.error("知识库文档上传失败: fileName={}, error={}",
                        file.getOriginalFilename(), error.getMessage()));
    }

    /**
     * 批量上传文档
     */
    @PostMapping("/bases/{knowledgeBaseId}/documents/batch")
    public Mono<DocumentBatchUploadDto> batchUploadDocuments(@PathVariable Long knowledgeBaseId,
                                                             @RequestParam("files") MultipartFile[] files,
                                                             @RequestParam(value = "parseMode", defaultValue = "auto") String parseMode,
                                                             @RequestParam(value = "enableVectorization", defaultValue = "true") Boolean enableVectorization,
                                                             Authentication authentication) {

        log.info("批量上传文档请求: knowledgeBaseId={}, fileCount={}, parseMode={}, enableVectorization={}",
                knowledgeBaseId, files.length, parseMode, enableVectorization);

        return uploadFacade.batchUploadDocuments(knowledgeBaseId, files, parseMode, enableVectorization, authentication)
                .doOnSuccess(result -> log.info("批量上传完成: 成功={}, 失败={}",
                        result.getSuccessDocuments().size(), result.getFailedDocuments().size()))
                .doOnError(error -> log.error("批量上传文档失败: knowledgeBaseId={}, error={}",
                        knowledgeBaseId, error.getMessage()));
    }

    /**
     * 获取知识库的文档列表
     */
    @GetMapping("/bases/{knowledgeBaseId}/documents")
    public Flux<DocumentDto> getDocuments(@PathVariable Long knowledgeBaseId,
                                          @RequestParam(defaultValue = "0") Integer page,
                                          @RequestParam(defaultValue = "10") Integer size,
                                          Authentication authentication) {
        log.info("查询知识库文档列表: knowledgeBaseId={}, page={}, size={}", knowledgeBaseId, page, size);

        return accessContextResolver.resolve(authentication)
                .flatMapMany(access -> documentService.getDocuments(
                        knowledgeBaseId, page, size, access.userId(), access.isAdmin()))
                .doOnComplete(() -> log.info("知识库文档列表查询完成: knowledgeBaseId={}", knowledgeBaseId))
                .doOnError(error -> log.error("知识库文档列表查询失败: knowledgeBaseId={}, error={}", knowledgeBaseId, error.getMessage()));
    }

    /**
     * 管理员视角查询全站文档列表
     */
    @GetMapping("/admin/documents")
    public Flux<DocumentDto> getAllDocumentsForAdmin(@RequestParam(defaultValue = "0") Integer page,
                                                     @RequestParam(defaultValue = "20") Integer size) {
        log.info("管理员查询全站文档列表: page={}, size={}", page, size);

        return documentService.getAllDocuments(page, size)
                .doOnComplete(() -> log.info("管理员全站文档列表查询完成"))
                .doOnError(error -> log.error("管理员全站文档列表查询失败: error={}", error.getMessage()));
    }

    /**
     * 管理员视角分页查询全站文档，包含筛选条件与真实聚合统计。
     */
    @GetMapping("/admin/documents/page")
    public Mono<AdminPageDto<DocumentDto>> getAllDocumentsPageForAdmin(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String parseStatus,
            @RequestParam(required = false) String embeddingStatus) {
        return documentService.getAllDocumentsPage(page, size, keyword, parseStatus, embeddingStatus);
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/documents/{id}")
    public Mono<DocumentDto> getDocument(@PathVariable Long id, Authentication authentication) {
        log.info("查询文档详情: id={}", id);

        return accessContextResolver.resolve(authentication)
                .flatMap(access -> documentService.getDocument(id, access.userId(), access.isAdmin()))
                .doOnSuccess(dto -> log.info("文档详情查询成功: id={}, fileName={}", id, dto.getFileName()))
                .doOnError(error -> log.error("文档详情查询失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/documents/{id}")
    public Mono<Void> deleteDocument(@PathVariable Long id, Authentication authentication) {
        log.info("删除文档请求: id={}", id);

        return accessContextResolver.resolve(authentication)
                .flatMap(access -> documentService.deleteDocument(id, access.userId(), access.isAdmin()))
                .doOnSuccess(v -> log.info("文档删除成功: id={}", id))
                .doOnError(error -> log.error("文档删除失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 下载文档
     */
    @GetMapping("/documents/{id}/download")
    public Mono<ResponseEntity<Object>> downloadDocument(@PathVariable Long id, Authentication authentication) {
        log.info("下载文档请求: id={}", id);

        return accessContextResolver.resolve(authentication)
                .flatMap(access -> documentService.getDocument(id, access.userId(), access.isAdmin()))
                .flatMap(documentDto -> {
                    if (documentDto.getOssUrl() == null) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }

                    // 返回重定向响应到OSS URL
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("Location", documentDto.getOssUrl());
                    headers.add("Content-Disposition",
                        "attachment; filename=\"" + documentDto.getFileName() + "\"");

                    log.info("文档下载重定向: id={}, fileName={}, url={}",
                        id, documentDto.getFileName(), documentDto.getOssUrl());

                    return Mono.just(ResponseEntity.status(HttpStatus.FOUND)
                            .headers(headers)
                            .build());
                })
                .doOnError(error -> log.error("文档下载失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 重新解析文档
     */
    @PostMapping("/documents/{id}/reparse")
    public Mono<DocumentDto> reparseDocument(@PathVariable Long id, Authentication authentication) {
        log.info("重新解析文档请求: id={}", id);

        return accessContextResolver.resolve(authentication)
                .flatMap(access -> documentService.reparseDocument(id, access.userId(), access.isAdmin()))
                .doOnSuccess(dto -> log.info("重新解析文档成功: id={}, fileName={}", id, dto.getFileName()))
                .doOnError(error -> log.error("重新解析文档失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 重新向量化文档
     */
    @PostMapping("/documents/{id}/re-embed")
    public Mono<DocumentDto> reEmbedDocument(@PathVariable Long id, Authentication authentication) {
        log.info("重新向量化文档请求: id={}", id);

        return accessContextResolver.resolve(authentication)
                .flatMap(access -> documentService.reEmbedDocument(id, access.userId(), access.isAdmin()))
                .doOnSuccess(dto -> log.info("重新向量化文档成功: id={}, fileName={}", id, dto.getFileName()))
                .doOnError(error -> log.error("重新向量化文档失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 检查文档向量化状态（调试用）
     */
    @GetMapping("/documents/{id}/embedding-status")
    public Mono<Map<String, Object>> checkDocumentEmbeddingStatus(@PathVariable Long id, Authentication authentication) {
        log.info("检查文档向量化状态: id={}", id);

        return accessContextResolver.resolve(authentication)
                .flatMap(access -> documentService.getDocument(id, access.userId(), access.isAdmin()))
                .flatMap(documentDto -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("documentId", id);
                    status.put("fileName", documentDto.getFileName());
                    status.put("parseStatus", documentDto.getParseStatus());
                    status.put("embeddingStatus", documentDto.getEmbeddingStatus());
                    status.put("isEmbedded", documentDto.getIsEmbedded());
                    status.put("chunkCount", documentDto.getChunkCount());
                    status.put("characterCount", documentDto.getCharacterCount());

                    // 查询文档块的向量化状态
                    return documentChunkService.getDocumentChunks(id)
                            .collectList()
                            .map(chunks -> {
                                long embeddedChunks = chunks.stream()
                                        .mapToLong(chunk -> Boolean.TRUE.equals(chunk.getIsEmbedded()) ? 1 : 0)
                                        .sum();

                                status.put("totalChunks", chunks.size());
                                status.put("embeddedChunks", embeddedChunks);
                                status.put("embeddingProgress",
                                        chunks.size() > 0 ? (double) embeddedChunks / chunks.size() * 100 : 0);

                                // 统计各状态的数量
                                Map<String, Long> statusCounts = chunks.stream()
                                        .collect(java.util.stream.Collectors.groupingBy(
                                                chunk -> chunk.getEmbeddingStatus() != null ? chunk.getEmbeddingStatus() : "NULL",
                                                java.util.stream.Collectors.counting()));
                                status.put("chunkStatusCounts", statusCounts);

                                // 判断算法是否正确
                                boolean actuallyEmbedded = embeddedChunks == chunks.size() && chunks.size() > 0;
                                status.put("shouldBeEmbedded", actuallyEmbedded);
                                status.put("statusMismatch", actuallyEmbedded != documentDto.getIsEmbedded());

                                status.put("timestamp", System.currentTimeMillis());
                                return status;
                            });
                })
                .doOnSuccess(result -> log.info("文档向量化状态检查完成: id={}", id))
                .doOnError(error -> log.error("文档向量化状态检查失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 获取文档处理记录
     */
    @GetMapping("/documents/{id}/processing-records")
    public Flux<org.example.ai_agent.entity.DocumentProcessingRecord> getDocumentProcessingRecords(@PathVariable Long id,
                                                                                                   Authentication authentication) {
        log.info("获取文档处理记录请求: documentId={}", id);

        return accessContextResolver.resolve(authentication)
            .flatMapMany(access -> documentService.getDocument(id, access.userId(), access.isAdmin())
                .thenMany(recordService.getDocumentProcessingRecords(id)))
            .doOnComplete(() -> log.info("文档处理记录获取完成: documentId={}", id))
            .doOnError(error -> log.error("获取文档处理记录失败: documentId={}, error={}", id, error.getMessage()));
    }

    /**
     * 获取失败的处理记录
     */
    @GetMapping("/processing-records/failed")
    public Flux<org.example.ai_agent.entity.DocumentProcessingRecord> getFailedProcessingRecords(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            Authentication authentication) {
        log.info("获取失败处理记录请求: page={}, size={}", page, size);

        return accessContextResolver.requireAdmin(authentication)
            .thenMany(recordService.getFailedProcessingRecords(page, size))
            .doOnComplete(() -> log.info("失败处理记录获取完成: page={}, size={}", page, size))
            .doOnError(error -> log.error("获取失败处理记录失败: error={}", error.getMessage()));
    }

    /**
     * 获取处理统计信息
     */
    @GetMapping("/processing-statistics")
    public Mono<DocumentProcessingRecordService.ProcessingStatistics> getProcessingStatistics(Authentication authentication) {
        log.info("获取处理统计信息请求");

        return accessContextResolver.requireAdmin(authentication)
            .then(recordService.getProcessingStatistics())
            .doOnSuccess(stats -> log.info("处理统计信息获取成功: {}", stats))
            .doOnError(error -> log.error("获取处理统计信息失败: error={}", error.getMessage()));
    }

}
