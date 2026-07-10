package org.example.ai_agent.controller.support;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
public class KnowledgeDocumentUploadFacade {

    private final DocumentService documentService;
    private final KnowledgeAccessContextResolver accessContextResolver;
    private final ReactiveMultipartFormReader formReader;

    public KnowledgeDocumentUploadFacade(DocumentService documentService,
                                         KnowledgeAccessContextResolver accessContextResolver,
                                         ReactiveMultipartFormReader formReader) {
        this.documentService = documentService;
        this.accessContextResolver = accessContextResolver;
        this.formReader = formReader;
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
                                    .flatMap(validUploaderId -> formReader.toMultipartFile(filePart)
                                            .flatMap(multipartFile -> documentService.uploadDocument(
                                                    multipartFile, knowledgeBaseId, parseMode,
                                                    enableVectorization, validUploaderId, true)));
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
        return accessContextResolver.requireUser(authentication)
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
                .flatMap(access -> formReader.toMultipartFile(filePart)
                        .flatMap(multipartFile -> documentService.uploadDocument(
                                multipartFile, knowledgeBaseId, parseMode,
                                enableVectorization, access.userId(), false)));
    }

    private Long parseKnowledgeBaseId(String value) {
        return Long.parseLong(value.trim());
    }
}
