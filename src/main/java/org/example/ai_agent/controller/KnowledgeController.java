package org.example.ai_agent.controller;

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
 * 知识库控制器
 * <p>
 * 提供知识库管理、文档上传、搜索、RAG对话等API
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
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
    private org.example.ai_agent.util.JwtUtil jwtUtil;

    /**
     * 搜索知识库
     */
    @PostMapping("/search")
    public Mono<KnowledgeSearchResultDto> searchKnowledge(
            @RequestBody KnowledgeSearchDto searchRequest) {

        log.info("知识库搜索请求: query={}, knowledgeBaseIds={}, maxResults={}",
                searchRequest.getQuery(), searchRequest.getKnowledgeBaseIds(), searchRequest.getMaxResults());

        return ragService.searchKnowledge(searchRequest)
                .doOnSuccess(result -> log.info("知识库搜索完成: resultCount={}, searchTime={}ms",
                        result.getTotalCount(), result.getSearchTime()))
                .doOnError(error -> log.error("知识库搜索失败: query={}, error={}",
                        searchRequest.getQuery(), error.getMessage()));
    }

    /**
     * RAG对话 - 非流式
     */
    @PostMapping("/chat")
    public Mono<RagChatResponseDto> ragChat(@RequestBody RagChatDto ragRequest) {

        log.info("RAG对话请求: question={}, modelId={}, knowledgeBaseIds={}",
                ragRequest.getQuestion(), ragRequest.getModelId(), ragRequest.getKnowledgeBaseIds());

        return ragService.ragChat(ragRequest)
                .doOnSuccess(response -> log.info("RAG对话完成: question={}, processingTime={}ms",
                        ragRequest.getQuestion(), response.getProcessingTime()))
                .doOnError(error -> log.error("RAG对话失败: question={}, error={}",
                        ragRequest.getQuestion(), error.getMessage()));
    }

    /**
     * RAG对话 - 流式
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> ragChatStream(@RequestBody RagChatDto ragRequest) {

        log.info("RAG流式对话请求: question={}, modelId={}, knowledgeBaseIds={}",
                ragRequest.getQuestion(), ragRequest.getModelId(), ragRequest.getKnowledgeBaseIds());

        return ragService.ragChatStream(ragRequest)
                .doOnSubscribe(subscription -> log.info("开始RAG流式对话: question={}", ragRequest.getQuestion()))
                .doOnComplete(() -> log.info("RAG流式对话完成: question={}", ragRequest.getQuestion()))
                .doOnError(error -> log.error("RAG流式对话失败: question={}, error={}",
                        ragRequest.getQuestion(), error.getMessage()));
    }

    /**
     * 创建知识库
     */
    @PostMapping("/bases")
    public Mono<KnowledgeBaseDto> createKnowledgeBase(@RequestBody KnowledgeBaseCreateDto createRequest,
                                                     @RequestHeader(org.springframework.http.HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        log.info("创建知识库请求: name={}, description={}, isPublic={}, embeddingModel={}",
                createRequest.getName(), createRequest.getDescription(),
                createRequest.getIsPublic(), createRequest.getEmbeddingModel());

        // 从JWT token中获取用户ID
        String token = authorizationHeader.substring(7).trim(); // 移除 "Bearer " 前缀
        Long creatorId = jwtUtil.getUserIdFromToken(token);
        
        if (creatorId == null) {
            return Mono.error(new IllegalArgumentException("无效的Token: 无法获取用户ID"));
        }

        return knowledgeBaseService.createKnowledgeBase(createRequest, creatorId)
                .doOnSuccess(dto -> log.info("知识库创建成功: id={}, name={}, creatorId={}", dto.getId(), dto.getName(), creatorId))
                .doOnError(error -> log.error("知识库创建失败: name={}, error={}",
                        createRequest.getName(), error.getMessage()));
    }

    /**
     * 获取知识库列表
     */
    @GetMapping("/bases")
    public Flux<KnowledgeBaseDto> getKnowledgeBases(
            @RequestParam(required = false) Boolean publicOnly,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        log.info("查询知识库列表: publicOnly={}, page={}, size={}", publicOnly, page, size);

        return knowledgeBaseService.getKnowledgeBases(publicOnly, page, size)
                .doOnComplete(() -> log.info("知识库列表查询完成"))
                .doOnError(error -> log.error("知识库列表查询失败: error={}", error.getMessage()));
    }

    /**
     * 获取知识库详情
     */
    @GetMapping("/bases/{id}")
    public Mono<KnowledgeBaseDto> getKnowledgeBase(@PathVariable Long id) {
        log.info("查询知识库详情: id={}", id);

        return knowledgeBaseService.getKnowledgeBase(id)
                .doOnSuccess(dto -> log.info("知识库详情查询成功: id={}, name={}", id, dto.getName()))
                .doOnError(error -> log.error("知识库详情查询失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 更新知识库
     */
    @PutMapping("/bases/{id}")
    public Mono<KnowledgeBaseDto> updateKnowledgeBase(@PathVariable Long id, @RequestBody KnowledgeBaseCreateDto updateRequest) {
        log.info("更新知识库请求: id={}, name={}", id, updateRequest.getName());

        return knowledgeBaseService.updateKnowledgeBase(id, updateRequest)
                .doOnSuccess(dto -> log.info("知识库更新成功: id={}, name={}", id, dto.getName()))
                .doOnError(error -> log.error("知识库更新失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/bases/{id}")
    public Mono<Void> deleteKnowledgeBase(@PathVariable Long id) {
        log.info("删除知识库请求: id={}", id);

        return knowledgeBaseService.deleteKnowledgeBase(id)
                .doOnSuccess(v -> log.info("知识库删除成功: id={}", id))
                .doOnError(error -> log.error("知识库删除失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 测试知识库是否存在
     */
    @GetMapping("/bases/{id}/exists")
    public Mono<Boolean> checkKnowledgeBaseExists(@PathVariable Long id) {
        log.info("检查知识库是否存在: id={}", id);

        return knowledgeBaseService.existsById(id)
                .doOnSuccess(exists -> log.info("知识库存在检查结果: id={}, exists={}", id, exists))
                .doOnError(error -> log.error("知识库存在检查失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 上传文档（前端专用接口） - 兼容性更好的版本
     */
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentDto> uploadDocumentDirect(ServerWebExchange exchange) {
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
                                        return convertFilePartToMultipartFile(filePartTyped)
                                                .flatMap(multipartFile -> {
                                                    // 从请求头中获取用户ID（传统方式不支持JWT，使用默认值）
                                                    Long uploaderId = 1L; // 前端上传无法传递JWT，使用默认值
                                                    return documentService.uploadDocument(multipartFile, knowledgeBaseId, parseMode, enableVectorization, uploaderId);
                                                });

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
     * 上传文档到知识库
     */
    @PostMapping("/bases/{knowledgeBaseId}/documents")
    public Mono<DocumentDto> uploadDocument(@PathVariable Long knowledgeBaseId,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "parseMode", defaultValue = "auto") String parseMode,
                                            @RequestParam(value = "enableVectorization", defaultValue = "true") Boolean enableVectorization,
                                            @RequestHeader(org.springframework.http.HttpHeaders.AUTHORIZATION) String authorizationHeader) {

        log.info("知识库文档上传请求: knowledgeBaseId={}, fileName={}, parseMode={}, enableVectorization={}",
                knowledgeBaseId, file.getOriginalFilename(), parseMode, enableVectorization);

        // 从JWT token中获取用户ID
        String token = authorizationHeader.substring(7).trim();
        Long uploaderId = jwtUtil.getUserIdFromToken(token);
        
        if (uploaderId == null) {
            return Mono.error(new IllegalArgumentException("无效的Token: 无法获取用户ID"));
        }

        return documentService.uploadDocument(file, knowledgeBaseId, parseMode, enableVectorization, uploaderId)
                .doOnSuccess(dto -> log.info("知识库文档上传成功: id={}, fileName={}, uploaderId={}", dto.getId(), dto.getFileName(), uploaderId))
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
                                                             @RequestHeader(org.springframework.http.HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        
        log.info("批量上传文档请求: knowledgeBaseId={}, fileCount={}, parseMode={}, enableVectorization={}",
                knowledgeBaseId, files.length, parseMode, enableVectorization);

        // 从JWT token中获取用户ID
        String token = authorizationHeader.substring(7).trim();
        Long uploaderId = jwtUtil.getUserIdFromToken(token);
        
        if (uploaderId == null) {
            return Mono.error(new IllegalArgumentException("无效的Token: 无法获取用户ID"));
        }

        // 批量处理文件上传
        return Flux.fromArray(files)
                .flatMap(file -> {
                    return documentService.uploadDocument(file, knowledgeBaseId, parseMode, enableVectorization, uploaderId)
                            .map(DocumentBatchUploadDto.SuccessDocument::new)
                            .cast(Object.class)
                            .onErrorReturn(new DocumentBatchUploadDto.FailedDocument(
                                    file.getOriginalFilename(), 
                                    "文件上传失败"));
                }, 3) // 限制并发数为3
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
                        knowledgeBaseId, error.getMessage()));
    }

    /**
     * 获取知识库的文档列表
     */
    @GetMapping("/bases/{knowledgeBaseId}/documents")
    public Flux<DocumentDto> getDocuments(@PathVariable Long knowledgeBaseId,
                                          @RequestParam(defaultValue = "0") Integer page,
                                          @RequestParam(defaultValue = "10") Integer size) {
        log.info("查询知识库文档列表: knowledgeBaseId={}, page={}, size={}", knowledgeBaseId, page, size);

        return documentService.getDocuments(knowledgeBaseId, page, size)
                .doOnComplete(() -> log.info("知识库文档列表查询完成: knowledgeBaseId={}", knowledgeBaseId))
                .doOnError(error -> log.error("知识库文档列表查询失败: knowledgeBaseId={}, error={}", knowledgeBaseId, error.getMessage()));
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/documents/{id}")
    public Mono<DocumentDto> getDocument(@PathVariable Long id) {
        log.info("查询文档详情: id={}", id);

        return documentService.getDocument(id)
                .doOnSuccess(dto -> log.info("文档详情查询成功: id={}, fileName={}", id, dto.getFileName()))
                .doOnError(error -> log.error("文档详情查询失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/documents/{id}")
    public Mono<Void> deleteDocument(@PathVariable Long id) {
        log.info("删除文档请求: id={}", id);

        return documentService.deleteDocument(id)
                .doOnSuccess(v -> log.info("文档删除成功: id={}", id))
                .doOnError(error -> log.error("文档删除失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 下载文档
     */
    @GetMapping("/documents/{id}/download")
    public Mono<ResponseEntity<Object>> downloadDocument(@PathVariable Long id) {
        log.info("下载文档请求: id={}", id);

        return documentService.getDocument(id)
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
    public Mono<DocumentDto> reparseDocument(@PathVariable Long id) {
        log.info("重新解析文档请求: id={}", id);

        return documentService.reparseDocument(id)
                .doOnSuccess(dto -> log.info("重新解析文档成功: id={}, fileName={}", id, dto.getFileName()))
                .doOnError(error -> log.error("重新解析文档失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 重新向量化文档
     */
    @PostMapping("/documents/{id}/re-embed")
    public Mono<DocumentDto> reEmbedDocument(@PathVariable Long id) {
        log.info("重新向量化文档请求: id={}", id);

        return documentService.reEmbedDocument(id)
                .doOnSuccess(dto -> log.info("重新向量化文档成功: id={}, fileName={}", id, dto.getFileName()))
                .doOnError(error -> log.error("重新向量化文档失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 检查文档向量化状态（调试用）
     */
    @GetMapping("/documents/{id}/embedding-status")
    public Mono<Map<String, Object>> checkDocumentEmbeddingStatus(@PathVariable Long id) {
        log.info("检查文档向量化状态: id={}", id);

        return documentService.getDocument(id)
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
    public Flux<org.example.ai_agent.entity.DocumentProcessingRecord> getDocumentProcessingRecords(@PathVariable Long id) {
        log.info("获取文档处理记录请求: documentId={}", id);
        
        return recordService.getDocumentProcessingRecords(id)
            .doOnComplete(() -> log.info("文档处理记录获取完成: documentId={}", id))
            .doOnError(error -> log.error("获取文档处理记录失败: documentId={}, error={}", id, error.getMessage()));
    }
    
    /**
     * 获取失败的处理记录
     */
    @GetMapping("/processing-records/failed")
    public Flux<org.example.ai_agent.entity.DocumentProcessingRecord> getFailedProcessingRecords(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        log.info("获取失败处理记录请求: page={}, size={}", page, size);
        
        return recordService.getFailedProcessingRecords(page, size)
            .doOnComplete(() -> log.info("失败处理记录获取完成: page={}, size={}", page, size))
            .doOnError(error -> log.error("获取失败处理记录失败: error={}", error.getMessage()));
    }
    
    /**
     * 获取处理统计信息
     */
    @GetMapping("/processing-statistics")
    public Mono<DocumentProcessingRecordService.ProcessingStatistics> getProcessingStatistics() {
        log.info("获取处理统计信息请求");
        
        return recordService.getProcessingStatistics()
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
    }
