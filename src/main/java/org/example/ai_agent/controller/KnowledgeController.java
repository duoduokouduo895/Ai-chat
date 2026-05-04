package org.example.ai_agent.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.chat.RagChatDto;
import org.example.ai_agent.dto.chat.RagChatResponseDto;
import org.example.ai_agent.dto.document.DocumentBatchUploadDto;
import org.example.ai_agent.dto.document.DocumentDto;
import org.example.ai_agent.dto.document.DocumentUploadDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseCreateDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchDto;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchResultDto;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.DocumentProcessingRecordService;
import org.example.ai_agent.service.DocumentChunkService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.MultiValueMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
@CrossOrigin(origins = {"https://dduo.chat", "http://dduo.chat", "http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
public class KnowledgeController {

    @Autowired
    private RagService ragService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentProcessingRecordService recordService;

    @Autowired
    private DocumentChunkService documentChunkService;

    @Autowired
    private UserRepository userRepository;
    /**
     * 搜索知识库
     */
    @PostMapping("/search")
    public Mono<KnowledgeSearchResultDto> searchKnowledge(
            @RequestBody KnowledgeSearchDto searchRequest,
            Authentication authentication) {

        log.info("知识库搜索请求: query={}, knowledgeBaseIds={}, maxResults={}",
                searchRequest.getQuery(), searchRequest.getKnowledgeBaseIds(), searchRequest.getMaxResults());

        return resolveAccessContext(authentication)
                .flatMap(access -> validateKnowledgeBaseReadAccess(searchRequest.getKnowledgeBaseIds(), access)
                        .then(ragService.searchKnowledge(searchRequest)))
                .doOnSuccess(result -> log.info("知识库搜索完成: resultCount={}, searchTime={}ms",
                        result.getTotalCount(), result.getSearchTime()))
                .doOnError(error -> log.error("知识库搜索失败: query={}, error={}",
                        searchRequest.getQuery(), error.getMessage()));
    }

    /**
     * 旧 RAG 对话兼容接口。
     * 主推荐入口已经收敛到 /api/ai/chat。
     */
    @Deprecated
    @PostMapping("/chat")
    public Mono<RagChatResponseDto> ragChat(@RequestBody RagChatDto ragRequest,
                                            Authentication authentication) {

        log.info("RAG对话请求: question={}, modelId={}, knowledgeBaseIds={}",
                ragRequest.getQuestion(), ragRequest.getModelId(), ragRequest.getKnowledgeBaseIds());

        return resolveAccessContext(authentication)
                .flatMap(access -> validateKnowledgeBaseReadAccess(ragRequest.getKnowledgeBaseIds(), access)
                        .then(ragService.ragChat(ragRequest)))
                .doOnSuccess(response -> log.info("RAG对话完成: question={}, processingTime={}ms",
                        ragRequest.getQuestion(), response.getProcessingTime()))
                .doOnError(error -> log.error("RAG对话失败: question={}, error={}",
                        ragRequest.getQuestion(), error.getMessage()));
    }

    /**
     * 旧 RAG 流式对话兼容接口。
     * 主推荐入口已经收敛到 /api/ai/chat。
     */
    @Deprecated
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public Flux<String> ragChatStream(@RequestBody RagChatDto ragRequest,
                                      Authentication authentication) {

        log.info("RAG流式对话请求: question={}, modelId={}, knowledgeBaseIds={}",
                ragRequest.getQuestion(), ragRequest.getModelId(), ragRequest.getKnowledgeBaseIds());

        return resolveAccessContext(authentication)
                .flatMapMany(access -> validateKnowledgeBaseReadAccess(ragRequest.getKnowledgeBaseIds(), access)
                        .thenMany(ragService.ragChatStream(ragRequest)))
                .doOnSubscribe(subscription -> log.info("开始RAG流式对话: question={}", ragRequest.getQuestion()))
                .doOnComplete(() -> log.info("RAG流式对话完成: question={}", ragRequest.getQuestion()))
                .doOnError(error -> log.error("RAG流式对话失败: question={}, error={}",
                        ragRequest.getQuestion(), error.getMessage()));
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

        return requireUserAccess(authentication)
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

        return requireAdminAccess(authentication)
                .then(requireExistingUserId(creatorId, "知识库归属用户不存在"))
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

        return resolveAccessContext(authentication)
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
     * 获取知识库详情
     */
    @GetMapping("/bases/{id}")
    public Mono<KnowledgeBaseDto> getKnowledgeBase(@PathVariable Long id, Authentication authentication) {
        log.info("查询知识库详情: id={}", id);

        return resolveAccessContext(authentication)
                .flatMap(access -> knowledgeBaseService.getKnowledgeBase(id, access.userId(), access.isAdmin()))
                .doOnSuccess(dto -> log.info("知识库详情查询成功: id={}, name={}", id, dto.getName()))
                .doOnError(error -> log.error("知识库详情查询失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 更新知识库
     */
    @PutMapping("/bases/{id}")
    public Mono<KnowledgeBaseDto> updateKnowledgeBase(@PathVariable Long id,
                                                      @Valid @RequestBody KnowledgeBaseCreateDto updateRequest,
                                                      Authentication authentication) {
        log.info("更新知识库请求: id={}, name={}", id, updateRequest.getName());

        return resolveAccessContext(authentication)
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

        return resolveAccessContext(authentication)
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

        return resolveAccessContext(authentication)
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
        return exchange.getMultipartData()
                .flatMap(multipartData -> {
                    try {
                        // 获取文件部分
                        org.springframework.http.codec.multipart.Part filePart = multipartData.getFirst("file");
                        org.springframework.http.codec.multipart.Part knowledgeBaseIdPart = multipartData.getFirst("knowledgeBaseId");
                        org.springframework.http.codec.multipart.Part parseModePart = multipartData.getFirst("parseMode");
                        org.springframework.http.codec.multipart.Part enableVectorizationPart = multipartData.getFirst("enableVectorization");

                        if (filePart == null || !(filePart instanceof FilePart)) {
                            return Mono.error(new IllegalArgumentException("文件不能为空"));
                        }

                        if (knowledgeBaseIdPart == null) {
                            return Mono.error(new IllegalArgumentException("知识库ID不能为空"));
                        }

                        FilePart filePartTyped = (FilePart) filePart;

                        // 使用响应式方式获取表单数据
                        Mono<String> knowledgeBaseIdMono = getPartContentAsString(knowledgeBaseIdPart);
                        Mono<String> parseModeMono = parseModePart != null ?
                                getPartContentAsString(parseModePart) : Mono.just("auto");
                        Mono<String> enableVectorizationMono = enableVectorizationPart != null ?
                                getPartContentAsString(enableVectorizationPart) : Mono.just("true");

                        return Mono.zip(knowledgeBaseIdMono, parseModeMono, enableVectorizationMono)
                                .flatMap(tuple -> {
                                    String knowledgeBaseIdStr = tuple.getT1();
                                    String parseMode = tuple.getT2();
                                    String enableVectorizationStr = tuple.getT3();

                                    try {
                                        Long knowledgeBaseId = Long.parseLong(knowledgeBaseIdStr.trim());
                                        Boolean enableVectorization = Boolean.parseBoolean(enableVectorizationStr.trim());

                                        log.info("文档上传请求: fileName={}, knowledgeBaseId={}, parseMode={}, enableVectorization={}",
                                                filePartTyped.filename(), knowledgeBaseId, parseMode, enableVectorization);

                                        // 将FilePart转换为MultipartFile
                                        return requireUserAccess(authentication)
                                                .flatMap(access -> convertFilePartToMultipartFile(filePartTyped)
                                                        .flatMap(multipartFile -> documentService.uploadDocument(
                                                                multipartFile, knowledgeBaseId, parseMode,
                                                                enableVectorization, access.userId(), false)));

                                    } catch (NumberFormatException e) {
                                        log.error("知识库ID格式错误: {}", knowledgeBaseIdStr, e);
                                        return Mono.error(new IllegalArgumentException("知识库ID格式错误"));
                                    } catch (Exception e) {
                                        log.error("文档上传请求处理失败", e);
                                        return Mono.error(new RuntimeException("文档上传请求处理失败: " + e.getMessage()));
                                    }
                                });

                    } catch (Exception e) {
                        log.error("文档上传请求处理失败", e);
                        return Mono.error(new RuntimeException("文档上传请求处理失败: " + e.getMessage()));
                    }
                })
                .doOnSuccess(dto -> log.info("文档上传成功: id={}, fileName={}", dto.getId(), dto.getFileName()))
                .doOnError(error -> log.error("文档上传失败: error={}", error.getMessage(), error));
    }

    /**
     * 管理员上传文档
     */
    @PostMapping(value = "/admin/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentDto> uploadDocumentDirectForAdmin(ServerWebExchange exchange, Authentication authentication) {
        return exchange.getMultipartData()
                .flatMap(multipartData -> {
                    try {
                        org.springframework.http.codec.multipart.Part filePart = multipartData.getFirst("file");
                        org.springframework.http.codec.multipart.Part knowledgeBaseIdPart = multipartData.getFirst("knowledgeBaseId");
                        org.springframework.http.codec.multipart.Part parseModePart = multipartData.getFirst("parseMode");
                        org.springframework.http.codec.multipart.Part enableVectorizationPart = multipartData.getFirst("enableVectorization");
                        org.springframework.http.codec.multipart.Part uploaderIdPart = multipartData.getFirst("uploaderId");

                        if (filePart == null || !(filePart instanceof FilePart)) {
                            return Mono.error(new IllegalArgumentException("文件不能为空"));
                        }

                        if (knowledgeBaseIdPart == null) {
                            return Mono.error(new IllegalArgumentException("知识库ID不能为空"));
                        }

                        FilePart filePartTyped = (FilePart) filePart;
                        Mono<String> knowledgeBaseIdMono = getPartContentAsString(knowledgeBaseIdPart);
                        Mono<String> parseModeMono = parseModePart != null
                                ? getPartContentAsString(parseModePart)
                                : Mono.just("auto");
                        Mono<String> enableVectorizationMono = enableVectorizationPart != null
                                ? getPartContentAsString(enableVectorizationPart)
                                : Mono.just("true");
                        Mono<String> uploaderIdMono = uploaderIdPart != null
                                ? getPartContentAsString(uploaderIdPart)
                                : Mono.just("");

                        return requireAdminAccess(authentication)
                                .then(Mono.zip(knowledgeBaseIdMono, parseModeMono, enableVectorizationMono, uploaderIdMono))
                                .flatMap(tuple -> {
                                    try {
                                        Long knowledgeBaseId = Long.parseLong(tuple.getT1().trim());
                                        String parseMode = tuple.getT2();
                                        Boolean enableVectorization = Boolean.parseBoolean(tuple.getT3().trim());
                                        Long uploaderId = parseOptionalLong(tuple.getT4());

                                        log.info("管理员文档上传请求: fileName={}, knowledgeBaseId={}, uploaderId={}, parseMode={}",
                                                filePartTyped.filename(), knowledgeBaseId, uploaderId, parseMode);

                                        return resolveAdminUploaderId(knowledgeBaseId, uploaderId)
                                                .flatMap(validUploaderId -> convertFilePartToMultipartFile(filePartTyped)
                                                        .flatMap(multipartFile -> documentService.uploadDocument(
                                                                multipartFile, knowledgeBaseId, parseMode,
                                                                enableVectorization, validUploaderId, true)));
                                    } catch (NumberFormatException e) {
                                        log.error("管理员文档上传参数格式错误", e);
                                        return Mono.error(new IllegalArgumentException("知识库ID或上传用户ID格式错误"));
                                    }
                                });
                    } catch (Exception e) {
                        log.error("管理员文档上传请求处理失败", e);
                        return Mono.error(new RuntimeException("管理员文档上传请求处理失败: " + e.getMessage()));
                    }
                })
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

        return requireUserAccess(authentication)
                .flatMap(access -> documentService.uploadDocument(
                                file, knowledgeBaseId, parseMode, enableVectorization, access.userId(), false)
                        .doOnSuccess(dto -> log.info("知识库文档上传成功: id={}, fileName={}, uploaderId={}",
                                dto.getId(), dto.getFileName(), access.userId()))
                        .doOnError(error -> log.error("知识库文档上传失败: fileName={}, error={}",
                                file.getOriginalFilename(), error.getMessage())));
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

        return requireUserAccess(authentication)
                .flatMap(access -> Flux.fromArray(files)
                        .flatMap(file -> documentService.uploadDocument(
                                        file, knowledgeBaseId, parseMode, enableVectorization, access.userId(), false)
                                .map(DocumentBatchUploadDto.SuccessDocument::new)
                                .cast(Object.class)
                                .onErrorReturn(new DocumentBatchUploadDto.FailedDocument(
                                        file.getOriginalFilename(),
                                        "文件上传失败")), 3)
                        .collectList()
                        .map(results -> {
                            DocumentBatchUploadDto response = new DocumentBatchUploadDto();
                            response.setSuccessDocuments(new java.util.ArrayList<>());
                            response.setFailedDocuments(new java.util.ArrayList<>());

                            for (Object result : results) {
                                if (result instanceof DocumentBatchUploadDto.SuccessDocument) {
                                    response.getSuccessDocuments().add(((DocumentBatchUploadDto.SuccessDocument) result).getDocument());
                                } else if (result instanceof DocumentBatchUploadDto.FailedDocument) {
                                    DocumentBatchUploadDto.DocumentUploadError error = new DocumentBatchUploadDto.DocumentUploadError();
                                    DocumentBatchUploadDto.FailedDocument failed = (DocumentBatchUploadDto.FailedDocument) result;
                                    error.setFileName(failed.getFileName());
                                    error.setErrorMessage(failed.getErrorMessage());
                                    response.getFailedDocuments().add(error);
                                }
                            }

                            return response;
                        })
                        .doOnSuccess(result -> log.info("批量上传完成: 成功={}, 失败={}",
                                result.getSuccessDocuments().size(), result.getFailedDocuments().size()))
                        .doOnError(error -> log.error("批量上传文档失败: knowledgeBaseId={}, error={}",
                                knowledgeBaseId, error.getMessage())));
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

        return resolveAccessContext(authentication)
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
     * 获取文档详情
     */
    @GetMapping("/documents/{id}")
    public Mono<DocumentDto> getDocument(@PathVariable Long id, Authentication authentication) {
        log.info("查询文档详情: id={}", id);

        return resolveAccessContext(authentication)
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

        return resolveAccessContext(authentication)
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

        return resolveAccessContext(authentication)
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

        return resolveAccessContext(authentication)
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

        return resolveAccessContext(authentication)
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

        return resolveAccessContext(authentication)
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

        return resolveAccessContext(authentication)
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

        return requireAdminAccess(authentication)
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

        return requireAdminAccess(authentication)
            .then(recordService.getProcessingStatistics())
            .doOnSuccess(stats -> log.info("处理统计信息获取成功: {}", stats))
            .doOnError(error -> log.error("获取处理统计信息失败: error={}", error.getMessage()));
    }

        /**
         * 获取Part内容为字符串
         */
        private Mono<String> getPartContentAsString (org.springframework.http.codec.multipart.Part part){
            return DataBufferUtils.join(part.content())
                    .map(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    });
        }

        /**
         * 从 multipart 数据中获取表单值 - 响应式版本
         */
        private Mono<String> getFormValueReactive
        (MultiValueMap < String, org.springframework.http.codec.multipart.Part > multipartData, String key){
            org.springframework.http.codec.multipart.Part part = multipartData.getFirst(key);
            if (part == null) {
                return Mono.empty();
            }

            return getPartContentAsString(part);
        }

        /**
         * 从 multipart 数据中获取表单值
         */
        private String getFormValue
        (MultiValueMap < String, org.springframework.http.codec.multipart.Part > multipartData, String key){
            return getFormValue(multipartData, key, null);
        }

        /**
         * 从 multipart 数据中获取表单值，带默认值
         */
        private String getFormValue
        (MultiValueMap < String, org.springframework.http.codec.multipart.Part > multipartData, String key, String
        defaultValue){
            org.springframework.http.codec.multipart.Part part = multipartData.getFirst(key);
            if (part == null) {
                return defaultValue;
            }

            return DataBufferUtils.join(part.content())
                    .map(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    })
                    .block(); // 这里使用block是安全的，因为数据量很小
        }

        /**
         * 将FilePart转换为MultipartFile的辅助方法
         */
        private Mono<MultipartFile> convertFilePartToMultipartFile (FilePart filePart){
            return DataBufferUtils.join(filePart.content())
                    .map(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);

                        return new MultipartFile() {
                            @Override
                            public String getName() {
                                return filePart.name();
                            }

                            @Override
                            public String getOriginalFilename() {
                                return filePart.filename();
                            }

                            @Override
                            public String getContentType() {
                                return filePart.headers().getContentType() != null ?
                                        filePart.headers().getContentType().toString() : "application/octet-stream";
                            }

                            @Override
                            public boolean isEmpty() {
                                return bytes.length == 0;
                            }

                            @Override
                            public long getSize() {
                                return bytes.length;
                            }

                            @Override
                            public byte[] getBytes() throws IOException {
                                return bytes;
                            }

                            @Override
                            public InputStream getInputStream() throws IOException {
                                return new ByteArrayInputStream(bytes);
                            }

                            @Override
                            public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
                                throw new UnsupportedOperationException("transferTo not supported");
                            }
                        };
                    });
        }

        private Mono<AccessContext> resolveAccessContext(Authentication authentication) {
            if (authentication == null || !authentication.isAuthenticated()) {
                return Mono.error(new SecurityException("当前用户未认证"));
            }

            boolean isAdmin = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("ROLE_ADMIN"::equals);
            Long actorId = extractActorId(authentication);
            return Mono.just(new AccessContext(actorId, isAdmin));
        }

        private Mono<AccessContext> requireUserAccess(Authentication authentication) {
            return resolveAccessContext(authentication)
                    .flatMap(access -> {
                        if (access.isAdmin()) {
                            return Mono.error(new SecurityException("管理员账号不能执行该操作"));
                        }
                        if (access.userId() == null || access.userId() <= 0) {
                            return Mono.error(new SecurityException("无法识别当前用户"));
                        }
                        return Mono.just(access);
                    });
        }

        private Mono<Void> requireAdminAccess(Authentication authentication) {
            return resolveAccessContext(authentication)
                    .flatMap(access -> access.isAdmin()
                            ? Mono.<Void>empty()
                            : Mono.error(new SecurityException("当前用户无权访问管理员资源")));
        }

        private Mono<Void> validateKnowledgeBaseReadAccess(java.util.List<Long> knowledgeBaseIds, AccessContext access) {
            if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
                return Mono.empty();
            }

            return Flux.fromIterable(knowledgeBaseIds)
                    .concatMap(knowledgeBaseId -> knowledgeBaseService
                            .getAuthorizedKnowledgeBase(knowledgeBaseId, access.userId(), access.isAdmin(), false)
                            .then())
                    .then();
        }

        private Mono<Long> resolveAdminUploaderId(Long knowledgeBaseId, Long uploaderId) {
            if (uploaderId != null) {
                return requireExistingUserId(uploaderId, "上传用户不存在");
            }

            return knowledgeBaseService.getKnowledgeBase(knowledgeBaseId)
                    .flatMap(knowledgeBase -> requireExistingUserId(
                            knowledgeBase.getCreatorId(), "知识库归属用户不存在"));
        }

        private Mono<Long> requireExistingUserId(Long userId, String errorMessage) {
            if (userId == null || userId <= 0) {
                return Mono.error(new IllegalArgumentException(errorMessage));
            }

            return userRepository.findById(userId)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException(errorMessage)))
                    .map(user -> user.getUserId());
        }

        private Long parseOptionalLong(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return Long.parseLong(value.trim());
        }

        private Long extractActorId(Authentication authentication) {
            Object details = authentication.getDetails();
            if (details instanceof Long id) {
                return id;
            }
            if (details instanceof Number number) {
                return number.longValue();
            }
            if (details instanceof String value && !value.isBlank()) {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }

        private record AccessContext(Long userId, boolean isAdmin) {}
    }


