package org.example.ai_agent.controller.support;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.properties.DocumentUploadProperties;
import org.example.ai_agent.dto.document.DocumentBatchUploadDto;
import org.example.ai_agent.dto.document.DocumentDto;
import org.example.ai_agent.service.DocumentService;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;

@Slf4j
@Component
public class KnowledgeDocumentUploadFacade {

    private final DocumentService documentService;
    private final KnowledgeAccessContextResolver accessContextResolver;
    private final ReactiveMultipartFormReader formReader;
    private final DocumentUploadProperties uploadProperties;

    public KnowledgeDocumentUploadFacade(DocumentService documentService,
                                         KnowledgeAccessContextResolver accessContextResolver,
                                         ReactiveMultipartFormReader formReader,
                                         DocumentUploadProperties uploadProperties) {
        this.documentService = documentService;
        this.accessContextResolver = accessContextResolver;
        this.formReader = formReader;
        this.uploadProperties = uploadProperties;
    }

    public Mono<DocumentDto> uploadDocumentDirect(ServerWebExchange exchange,
                                                  Authentication authentication) {
        return exchange.getMultipartData()
                .flatMap(multipartData -> Mono.zip(
                                formReader.getRequiredFile(multipartData, "file"),
                                formReader.getRequiredFormValue(multipartData, "knowledgeBaseId", "知识库ID不能为空"),
                                formReader.getFormValueOrDefault(multipartData, "parseMode", "auto"),
                                formReader.getFormValueOrDefault(multipartData, "enableVectorization", "true")
                        )
                        .flatMap(tuple -> {
                            FilePart filePart = tuple.getT1();
                            Long knowledgeBaseId = parseKnowledgeBaseId(tuple.getT2());
                            String parseMode = tuple.getT3();
                            Boolean enableVectorization = Boolean.parseBoolean(tuple.getT4().trim());

                            log.info("文档上传请求: fileName={}, knowledgeBaseId={}, parseMode={}, enableVectorization={}",
                                    filePart.filename(), knowledgeBaseId, parseMode, enableVectorization);

                            return uploadFilePart(filePart, knowledgeBaseId, parseMode,
                                    enableVectorization, authentication);
                        }))
                .onErrorMap(NumberFormatException.class,
                        error -> new IllegalArgumentException("知识库ID格式错误"));
    }

    public Mono<DocumentDto> uploadDocumentDirectForAdmin(ServerWebExchange exchange,
                                                          Authentication authentication) {
        return exchange.getMultipartData()
                .flatMap(multipartData -> Mono.zip(
                                formReader.getRequiredFile(multipartData, "file"),
                                formReader.getRequiredFormValue(multipartData, "knowledgeBaseId", "知识库ID不能为空"),
                                formReader.getFormValueOrDefault(multipartData, "parseMode", "auto"),
                                formReader.getFormValueOrDefault(multipartData, "enableVectorization", "true"),
                                formReader.getFormValueOrDefault(multipartData, "uploaderId", "")
                        )
                        .flatMap(tuple -> {
                            FilePart filePart = tuple.getT1();
                            Long knowledgeBaseId = parseKnowledgeBaseId(tuple.getT2());
                            String parseMode = tuple.getT3();
                            Boolean enableVectorization = Boolean.parseBoolean(tuple.getT4().trim());
                            Long uploaderId = accessContextResolver.parseOptionalLong(tuple.getT5());

                            log.info("管理员文档上传请求: fileName={}, knowledgeBaseId={}, uploaderId={}, parseMode={}",
                                    filePart.filename(), knowledgeBaseId, uploaderId, parseMode);

                            return accessContextResolver.requireAdmin(authentication)
                                    .then(accessContextResolver.resolveAdminUploaderId(knowledgeBaseId, uploaderId))
                                    .flatMap(validUploaderId -> uploadStagedFile(filePart, knowledgeBaseId, parseMode,
                                            enableVectorization, validUploaderId, true));
                        }))
                .onErrorMap(NumberFormatException.class,
                        error -> new IllegalArgumentException("知识库ID或上传用户ID格式错误"));
    }

    public Mono<DocumentDto> uploadDocument(Long knowledgeBaseId,
                                            MultipartFile file,
                                            String parseMode,
                                            Boolean enableVectorization,
                                            Authentication authentication) {
        return accessContextResolver.requireUser(authentication)
                .flatMap(access -> documentService.uploadDocument(
                        file, knowledgeBaseId, parseMode, enableVectorization, access.userId(), false));
    }

    public Mono<DocumentBatchUploadDto> batchUploadDocuments(Long knowledgeBaseId,
                                                             MultipartFile[] files,
                                                             String parseMode,
                                                             Boolean enableVectorization,
                                                             Authentication authentication) {
        if (files == null || files.length == 0) {
            return Mono.error(new IllegalArgumentException("请至少选择一个文件"));
        }
        if (files.length > uploadProperties.effectiveMaxBatchFiles()) {
            return Mono.error(new IllegalArgumentException("一次最多上传"
                    + uploadProperties.effectiveMaxBatchFiles() + "个文件"));
        }
        long totalBytes = Arrays.stream(files)
                .mapToLong(file -> file != null ? Math.max(0L, file.getSize()) : 0L)
                .sum();
        if (totalBytes > uploadProperties.effectiveMaxRequestBytes()) {
            return Mono.error(new IllegalArgumentException("本次上传总大小不能超过"
                    + uploadProperties.effectiveMaxRequestBytes() / (1024 * 1024) + "MB"));
        }
        return accessContextResolver.requireUser(authentication)
                .flatMap(access -> Flux.fromArray(files)
                        .flatMap(file -> documentService.uploadDocument(
                                        file, knowledgeBaseId, parseMode, enableVectorization, access.userId(), false)
                                .map(DocumentBatchUploadDto.SuccessDocument::new)
                                .cast(Object.class)
                                .onErrorReturn(new DocumentBatchUploadDto.FailedDocument(
                                        file.getOriginalFilename(),
                                        "文件上传失败")), Math.min(2, uploadProperties.effectiveMaxBatchFiles()))
                        .collectList()
                        .map(results -> {
                            DocumentBatchUploadDto response = new DocumentBatchUploadDto();
                            response.setSuccessDocuments(new ArrayList<>());
                            response.setFailedDocuments(new ArrayList<>());

                            for (Object result : results) {
                                if (result instanceof DocumentBatchUploadDto.SuccessDocument success) {
                                    response.getSuccessDocuments().add(success.getDocument());
                                } else if (result instanceof DocumentBatchUploadDto.FailedDocument failed) {
                                    DocumentBatchUploadDto.DocumentUploadError error =
                                            new DocumentBatchUploadDto.DocumentUploadError();
                                    error.setFileName(failed.getFileName());
                                    error.setErrorMessage(failed.getErrorMessage());
                                    response.getFailedDocuments().add(error);
                                }
                            }

                            return response;
                        }));
    }

    private Mono<DocumentDto> uploadFilePart(FilePart filePart,
                                             Long knowledgeBaseId,
                                             String parseMode,
                                             Boolean enableVectorization,
                                             Authentication authentication) {
        return accessContextResolver.requireUser(authentication)
                .flatMap(access -> uploadStagedFile(filePart, knowledgeBaseId, parseMode,
                        enableVectorization, access.userId(), false));
    }

    private Mono<DocumentDto> uploadStagedFile(FilePart filePart, Long knowledgeBaseId, String parseMode,
                                                Boolean enableVectorization, Long uploaderId, boolean isAdmin) {
        return Mono.usingWhen(formReader.stageToTemporaryFile(filePart),
                stagedFile -> documentService.uploadDocument(stagedFile, knowledgeBaseId, parseMode,
                        enableVectorization, uploaderId, isAdmin),
                this::closeStagedFile,
                (stagedFile, error) -> closeStagedFile(stagedFile),
                this::closeStagedFile);
    }

    private Mono<Void> closeStagedFile(ReactiveMultipartFormReader.TemporaryMultipartFile stagedFile) {
        return Mono.<Void>fromRunnable(() -> {
            try {
                stagedFile.close();
            } catch (java.io.IOException ignored) {
                // Temporary files are best-effort cleanup; upload outcome has already been decided.
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private Long parseKnowledgeBaseId(String value) {
        return Long.parseLong(value.trim());
    }
}
