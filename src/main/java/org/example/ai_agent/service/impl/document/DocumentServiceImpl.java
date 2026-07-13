package org.example.ai_agent.service.impl.document;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.admin.AdminPageDto;
import org.example.ai_agent.dto.document.AdminDocumentSummaryProjection;
import org.example.ai_agent.dto.document.DocumentDto;
import org.example.ai_agent.dto.document.DocumentListProjection;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.entity.KnowledgeDocument;
import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository;
import org.example.ai_agent.service.DocumentIngestionService;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.OssService;
import org.example.ai_agent.service.document.DocumentUploadValidator;
import org.example.ai_agent.service.impl.knowledge.KnowledgeAssetCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档服务实现类
 * <p>
 * 基于LangChain4j 1.0.1的文档处理服务实现
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {
    
    @Autowired
    private KnowledgeDocumentRepository documentRepository;
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    @Autowired
    private OssService ossService;
    
    @Autowired
    private DocumentIngestionService documentIngestionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KnowledgeAssetCleanupService knowledgeAssetCleanupService;

    @Autowired
    private DocumentUploadValidator documentUploadValidator;

    @Autowired
    private TransactionalOperator transactionalOperator;
    
    @Value("${knowledge-base.document.supported-types:pdf,docx,doc,txt,md,pptx,xlsx}")
    private String[] supportedTypes;
    
    @Override
    public Mono<DocumentDto> uploadDocument(MultipartFile file, Long knowledgeBaseId, String parseMode,
                                            Boolean enableVectorization, Long requesterUserId, boolean isAdmin) {
        return Mono.fromCallable(() -> {
            int fileNameLength = file.getOriginalFilename() == null ? 0 : file.getOriginalFilename().length();
            log.info("开始上传文档: fileNameLength={}, knowledgeBaseId={}, parseMode={}, enableVectorization={}",
                    fileNameLength, knowledgeBaseId, parseMode, enableVectorization);
            
            // 参数验证
            if (knowledgeBaseId == null) {
                throw new IllegalArgumentException("知识库ID不能为空");
            }
            documentUploadValidator.validate(file, supportedTypes);

            if (!isAdmin && (requesterUserId == null || requesterUserId <= 0)) {
                throw new SecurityException("当前用户无权上传文档");
            }
            
            return file;
        })
        .flatMap(validatedFile -> {
            return knowledgeBaseService.getAuthorizedKnowledgeBase(knowledgeBaseId, requesterUserId, isAdmin, true)
                .then(uploadToOss(validatedFile, knowledgeBaseId));
        })
        .flatMap(ossFileInfo -> saveDocumentRecordAndQueueIngestion(
                file, knowledgeBaseId, parseMode, enableVectorization,
                ossFileInfo.ossPath(), ossFileInfo.url(), requesterUserId))
        .map(this::toDto)
        .doOnSuccess(dto -> log.info("文档上传成功: id={}", dto.getId()))
        .doOnError(error -> log.error("文档上传失败: errorType={}", error.getClass().getSimpleName()))
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<DocumentDto> getDocuments(Long knowledgeBaseId, Integer page, Integer size,
                                          Long requesterUserId, boolean isAdmin) {
        return knowledgeBaseService.getAuthorizedKnowledgeBase(knowledgeBaseId, requesterUserId, isAdmin, false)
        .then(Mono.fromSupplier(() -> pageWindow(page, size, 10, 100)))
        .flatMapMany(window -> documentRepository.findSummariesByKnowledgeBaseId(
                knowledgeBaseId, window.limit(), window.offset()))
        .map(this::toListDto)
        .doOnComplete(() -> log.info("文档列表查询完成: knowledgeBaseId={}", knowledgeBaseId))
        .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<DocumentDto> getAllDocuments(Integer page, Integer size) {
        return Mono.fromSupplier(() -> pageWindow(page, size, 20, 200))
        .flatMapMany(window -> documentRepository.findAllSummaries(window.limit(), window.offset()))
        .map(this::toListDto)
        .doOnComplete(() -> log.info("全站文档列表查询完成: page={}, size={}", page, size))
        .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<AdminPageDto<DocumentDto>> getAllDocumentsPage(Integer page, Integer size, String keyword,
                                                               String parseStatus, String embeddingStatus) {
        PageWindow window = pageWindow(page, size, 10, 100);
        String finalKeyword = normalizeFilter(keyword);
        String finalParseStatus = normalizeFilter(parseStatus);
        String finalEmbeddingStatus = normalizeFilter(embeddingStatus);

        return Mono.zip(
                documentRepository.findAdminPage(finalKeyword, finalParseStatus, finalEmbeddingStatus,
                                window.limit(), window.offset())
                        .map(this::toListDto)
                        .collectList(),
                documentRepository.countAdminPage(finalKeyword, finalParseStatus, finalEmbeddingStatus),
                documentRepository.summarizeForAdmin()
                        .defaultIfEmpty(new AdminDocumentSummaryProjection())
        ).map(tuple -> new AdminPageDto<>(
                tuple.getT1(),
                tuple.getT2(),
                toAdminSummary(tuple.getT3())
        )).doOnSuccess(result -> log.info("管理员文档分页查询完成: page={}, size={}, total={}",
                page, size, result.getTotal()));
    }

    @Override
    public Flux<DocumentDto> getDocumentsByUploaderId(Long uploaderId, Integer page, Integer size) {
        return Mono.fromSupplier(() -> pageWindow(page, size, 10, 100))
        .flatMapMany(window -> documentRepository.findSummariesByUploaderId(
                uploaderId, window.limit(), window.offset()))
        .map(this::toListDto)
        .doOnComplete(() -> log.info("按上传用户查询文档完成: page={}, size={}", page, size))
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<DocumentDto> getDocument(Long id, Long requesterUserId, boolean isAdmin) {
        return getAuthorizedDocument(id, requesterUserId, isAdmin, false)
            .flatMap(this::toEnrichedDto)
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Void> deleteDocument(Long id, Long requesterUserId, boolean isAdmin) {
        return getAuthorizedDocument(id, requesterUserId, isAdmin, true)
            .flatMap(document -> {
                Long knowledgeBaseId = document.getKnowledgeBaseId();
                return knowledgeAssetCleanupService.deleteDocumentAggregate(document)
                    .then(knowledgeBaseService.adjustDocumentCount(knowledgeBaseId, -1)
                            .onErrorResume(error -> {
                            log.warn("减少知识库文档数量失败，等待定时校准: knowledgeBaseId={}, errorType={}",
                                    knowledgeBaseId, error.getClass().getSimpleName());
                                return Mono.empty();
                            }));
            })
            .doOnSuccess(v -> log.info("文档删除成功: id={}", id))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<DocumentDto> reparseDocument(Long id, Long requesterUserId, boolean isAdmin) {
        return getAuthorizedDocument(id, requesterUserId, isAdmin, true)
            .flatMap(document -> {
                log.info("重新解析文档: id={}", id);
                
                // 使用默认解析模式
                String parseMode = "auto";
                
                return documentIngestionService.reparseDocument(id, document.getKnowledgeBaseId(), parseMode)
                    .map(task -> {
                        log.info("重新解析已受理: documentId={}, firstStage={}, action={}",
                                task.documentId(), task.firstStage(), task.action());
                        return document;
                    });
            })
            .map(this::toDto)
            .doOnSuccess(dto -> log.info("重新解析文档完成: id={}", id))
            .doOnError(error -> log.error("重新解析文档失败: id={}, errorType={}", id, error.getClass().getSimpleName()))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<DocumentDto> reEmbedDocument(Long id, Long requesterUserId, boolean isAdmin) {
        return getAuthorizedDocument(id, requesterUserId, isAdmin, true)
            .flatMap(document -> {
                log.info("重新向量化文档: id={}", id);
                
                return documentIngestionService.reEmbedDocument(id, document.getKnowledgeBaseId())
                    .map(task -> {
                        log.info("重新向量化已受理: documentId={}, firstStage={}, action={}",
                                task.documentId(), task.firstStage(), task.action());
                        return document;
                    });
            })
            .map(this::toDto)
            .doOnSuccess(dto -> log.info("重新向量化文档完成: id={}", id))
            .doOnError(error -> log.error("重新向量化文档失败: id={}, errorType={}", id, error.getClass().getSimpleName()))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public boolean isSupportedFileType(String fileName) {
        if (fileName == null) {
            log.warn("文件名为空，不支持的文件类型");
            return false;
        }
        String extension = getFileExtension(fileName);
        List<String> supportedList = Arrays.asList(supportedTypes);
        boolean isSupported = supportedList.contains(extension.toLowerCase());
        
        log.info("文件类型检查: extension={}, isSupported={}", extension, isSupported);
        
        return isSupported;
    }
    
    @Override
    public DocumentDto toDto(KnowledgeDocument entity) {
        if (entity == null) {
            return null;
        }
        
        DocumentDto dto = new DocumentDto();
        dto.setId(entity.getId());
        dto.setKnowledgeBaseId(entity.getKnowledgeBaseId());
        dto.setTitle(entity.getTitle());
        dto.setFileName(entity.getFileName());
        dto.setFileType(entity.getFileType());
        dto.setFileSize(entity.getFileSize());
        dto.setOssPath(entity.getOssPath());
        dto.setOssUrl(entity.getOssUrl());
        dto.setParseStatus(entity.getParseStatus());
        dto.setContent(entity.getContent());
        dto.setCharacterCount(entity.getCharacterCount());
        dto.setChunkCount(entity.getChunkCount());
        
        // 根据实际情况动态计算是否已向量化，解决硬编码问题
        dto.setIsEmbedded(calculateIsEmbedded(entity));
        dto.setEmbeddingStatus(entity.getEmbeddingStatus());
        dto.setMetadata(entity.getMetadata());
        dto.setUploaderId(entity.getUploaderId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        
        return dto;
    }

    private Mono<DocumentDto> toEnrichedDto(KnowledgeDocument entity) {
        DocumentDto dto = toDto(entity);

        Mono<String> knowledgeBaseNameMono = Mono.justOrEmpty(entity.getKnowledgeBaseId())
                .flatMap(knowledgeBaseService::getKnowledgeBase)
                .map(KnowledgeBaseDto::getName)
                .onErrorResume(error -> {
                    log.warn("补全文档知识库名称失败: documentId={}, knowledgeBaseId={}, errorType={}",
                            entity.getId(), entity.getKnowledgeBaseId(), error.getClass().getSimpleName());
                    return Mono.empty();
                })
                .defaultIfEmpty("未知知识库");

        Mono<String> uploaderNameMono = Mono.justOrEmpty(entity.getUploaderId())
                .flatMap(userRepository::findById)
                .map(User::getUsername)
                .onErrorResume(error -> {
                    log.warn("补全文档上传者名称失败: documentId={}, errorType={}",
                            entity.getId(), error.getClass().getSimpleName());
                    return Mono.empty();
                })
                .defaultIfEmpty("未知用户");

        return Mono.zip(knowledgeBaseNameMono, uploaderNameMono)
                .map(tuple -> {
                    dto.setKnowledgeBaseName(tuple.getT1());
                    dto.setUploaderName(tuple.getT2());
                    return dto;
                });
    }

    private DocumentDto toListDto(DocumentListProjection projection) {
        DocumentDto dto = new DocumentDto();
        dto.setId(projection.getId());
        dto.setKnowledgeBaseId(projection.getKnowledgeBaseId());
        dto.setKnowledgeBaseName(defaultName(projection.getKnowledgeBaseName(), "未知知识库"));
        dto.setTitle(projection.getTitle());
        dto.setFileName(projection.getFileName());
        dto.setFileType(projection.getFileType());
        dto.setFileSize(projection.getFileSize());
        dto.setOssPath(projection.getOssPath());
        dto.setOssUrl(projection.getOssUrl());
        dto.setParseStatus(projection.getParseStatus());
        dto.setCharacterCount(projection.getCharacterCount());
        dto.setChunkCount(projection.getChunkCount());
        dto.setIsEmbedded(calculateIsEmbedded(
                projection.getChunkCount(), projection.getEmbeddingStatus(), projection.getIsEmbedded()));
        dto.setEmbeddingStatus(projection.getEmbeddingStatus());
        dto.setMetadata(projection.getMetadata());
        dto.setUploaderId(projection.getUploaderId());
        dto.setUploaderName(defaultName(projection.getUploaderName(), "未知用户"));
        dto.setCreatedAt(projection.getCreatedAt());
        dto.setUpdatedAt(projection.getUpdatedAt());
        return dto;
    }

    private String defaultName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Map<String, Object> toAdminSummary(AdminDocumentSummaryProjection projection) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("documentCount", defaultCount(projection.getDocumentCount()));
        summary.put("uploaderCount", defaultCount(projection.getUploaderCount()));
        summary.put("totalFileSize", defaultCount(projection.getTotalFileSize()));
        return summary;
    }

    private long defaultCount(Long value) {
        return value != null ? value : 0L;
    }

    private String normalizeFilter(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
    
    /**
     * 动态计算文档是否已向量化
     * 基于实际的文档块向量化状态进行判断
     */
    private Boolean calculateIsEmbedded(KnowledgeDocument document) {
        return calculateIsEmbedded(
                document.getChunkCount(), document.getEmbeddingStatus(), document.getIsEmbedded());
    }

    private Boolean calculateIsEmbedded(Integer chunkCount, String embeddingStatus, Boolean fallback) {
        // 如果文档还没有解析或没有分块，则肯定未向量化
        if (chunkCount == null || chunkCount <= 0) {
            return false;
        }
        
        // 如果向量化状态为COMPLETED，则认为已向量化
        if ("COMPLETED".equals(embeddingStatus)) {
            return true;
        }
        
        // 如果向量化状态为FAILED，则认为未向量化
        if ("FAILED".equals(embeddingStatus)) {
            return false;
        }
        
        // 其他情况（PENDING, PROCESSING）使用数据库中的值作为默认值
        return fallback != null ? fallback : false;
    }
    
    /**
     * 上传文件到OSS
     */
    private Mono<OssService.OssFileInfo> uploadToOss(MultipartFile file, Long knowledgeBaseId) {
        return Mono.usingWhen(
            Mono.fromCallable(file::getInputStream),
            inputStream -> ossService.uploadKnowledgeDocument(
                knowledgeBaseId,
                file.getOriginalFilename(),
                inputStream,
                file.getSize(),
                file.getContentType()
            ),
            inputStream -> closeInputStream(inputStream),
            (inputStream, error) -> closeInputStream(inputStream),
            inputStream -> closeInputStream(inputStream)
        )
        .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> closeInputStream(InputStream inputStream) {
        return Mono.fromRunnable(() -> {
            try {
                inputStream.close();
            } catch (Exception error) {
                log.warn("关闭上传文件输入流失败: errorType={}", error.getClass().getSimpleName());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
    
    /**
     * 保存文档记录到数据库
     */
    private Mono<KnowledgeDocument> saveDocumentRecord(MultipartFile file, Long knowledgeBaseId, 
                                                      String parseMode, Boolean enableVectorization, 
                                                      String ossPath, String ossUrl, Long uploaderId) {
        return Mono.fromCallable(() -> {
            KnowledgeDocument document = new KnowledgeDocument();
            document.setKnowledgeBaseId(knowledgeBaseId);
            document.setTitle(file.getOriginalFilename());
            document.setFileName(file.getOriginalFilename());
            document.setFileType(getFileExtension(file.getOriginalFilename()));
            document.setFileSize(file.getSize());
            document.setOssPath(ossPath);
            document.setOssUrl(ossUrl);
            document.setParseStatus("PENDING");
            document.setCharacterCount(0);
            document.setChunkCount(0);
            document.setIsEmbedded(false);
            document.setEmbeddingStatus("PENDING");
            document.setUploaderId(uploaderId);
            
            LocalDateTime now = LocalDateTime.now();
            document.setCreatedAt(now);
            document.setUpdatedAt(now);
            
            return document;
        })
        .flatMap(documentRepository::save)
        .flatMap(savedDoc -> knowledgeBaseService.adjustDocumentCount(knowledgeBaseId, 1).thenReturn(savedDoc))
        .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 文档记录、知识库计数和首个 outbox 事件必须一起提交。
     * 这样不会出现“文档显示待解析，但启动消息因进程崩溃永久丢失”的状态。
     */
    private Mono<KnowledgeDocument> saveDocumentRecordAndQueueIngestion(MultipartFile file,
                                                                          Long knowledgeBaseId,
                                                                          String parseMode,
                                                                          Boolean enableVectorization,
                                                                          String ossPath,
                                                                          String ossUrl,
                                                                          Long uploaderId) {
        return saveDocumentRecord(file, knowledgeBaseId, parseMode, enableVectorization, ossPath, ossUrl, uploaderId)
                .flatMap(document -> documentIngestionService.startIngestion(
                                document.getId(), knowledgeBaseId, parseMode, enableVectorization)
                        .map(task -> {
                            log.info("文档 ingestion 流水线已耐久化: documentId={}, firstStage={}, action={}",
                                    task.documentId(), task.firstStage(), task.action());
                            return document;
                        }))
                .as(transactionalOperator::transactional);
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    private Mono<KnowledgeDocument> getAuthorizedDocument(Long id, Long requesterUserId,
                                                          boolean isAdmin, boolean requireWriteAccess) {
        return documentRepository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("文档不存在: id=" + id)))
            .flatMap(document -> knowledgeBaseService
                    .getAuthorizedKnowledgeBase(document.getKnowledgeBaseId(), requesterUserId, isAdmin, requireWriteAccess)
                    .thenReturn(document));
    }

    private PageWindow pageWindow(Integer page, Integer size, int defaultSize, int maxSize) {
        int safePage = page != null && page >= 0 ? page : 0;
        int safeSize = size != null && size > 0 && size <= maxSize ? size : defaultSize;
        return new PageWindow(safeSize, (long) safePage * safeSize);
    }

    private record PageWindow(int limit, long offset) {
    }

}
