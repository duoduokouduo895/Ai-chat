package org.example.ai_agent.service.impl.document;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.document.DocumentDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.entity.KnowledgeDocument;
import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository;
import org.example.ai_agent.service.DocumentIngestionService;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.OssService;
import org.example.ai_agent.service.impl.knowledge.KnowledgeAssetCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

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
    
    @Value("${knowledge-base.document.supported-types:pdf,docx,doc,txt,md,pptx,xlsx}")
    private String[] supportedTypes;
    
    @Override
    public Mono<DocumentDto> uploadDocument(MultipartFile file, Long knowledgeBaseId, String parseMode,
                                            Boolean enableVectorization, Long requesterUserId, boolean isAdmin) {
        return Mono.fromCallable(() -> {
            log.info("开始上传文档: fileName={}, knowledgeBaseId={}, parseMode={}, enableVectorization={}", 
                    file.getOriginalFilename(), knowledgeBaseId, parseMode, enableVectorization);
            
            // 参数验证
            if (file.isEmpty()) {
                throw new IllegalArgumentException("文件不能为空");
            }
            
            if (knowledgeBaseId == null) {
                throw new IllegalArgumentException("知识库ID不能为空");
            }
            
            String fileName = file.getOriginalFilename();
            if (fileName == null) {
                throw new IllegalArgumentException("文件名不能为空");
            }
            
            // 检查文件类型
            if (!isSupportedFileType(fileName)) {
                throw new IllegalArgumentException("不支持的文件类型: " + getFileExtension(fileName));
            }
            
            // 检查文件大小 (50MB)
            if (file.getSize() > 50 * 1024 * 1024) {
                throw new IllegalArgumentException("文件大小不能超过50MB");
            }

            if (!isAdmin && (requesterUserId == null || requesterUserId <= 0)) {
                throw new SecurityException("当前用户无权上传文档");
            }
            
            return file;
        })
        .flatMap(validatedFile -> {
            return knowledgeBaseService.getAuthorizedKnowledgeBase(knowledgeBaseId, requesterUserId, isAdmin, true)
                .then(uploadToOss(validatedFile, knowledgeBaseId));
        })
        .flatMap(ossFileInfo -> {
            // 创建文档记录
            return saveDocumentRecord(file, knowledgeBaseId, parseMode, enableVectorization, 
                    ossFileInfo.ossPath(), ossFileInfo.url(), requesterUserId);
        })
        .flatMap(document -> {
            // 启动统一 ingestion 流水线
            return documentIngestionService.startIngestion(
                    document.getId(), knowledgeBaseId, parseMode, enableVectorization)
                .map(task -> {
                    log.info("文档 ingestion 流水线已受理: documentId={}, firstStage={}, action={}",
                            task.documentId(), task.firstStage(), task.action());
                    return document;
                });
        })
        .map(this::toDto)
        .doOnSuccess(dto -> log.info("文档上传成功: id={}, fileName={}", dto.getId(), dto.getFileName()))
        .doOnError(error -> log.error("文档上传失败: fileName={}, error={}", file.getOriginalFilename(), error.getMessage()))
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<DocumentDto> getDocuments(Long knowledgeBaseId, Integer page, Integer size,
                                          Long requesterUserId, boolean isAdmin) {
        return knowledgeBaseService.getAuthorizedKnowledgeBase(knowledgeBaseId, requesterUserId, isAdmin, false)
        .then(Mono.fromCallable(() -> {
            final int finalPage = page != null && page >= 0 ? page : 0;
            final int finalSize = size != null && size > 0 && size <= 100 ? size : 10;
            return PageRequest.of(finalPage, finalSize);
        }))
        .flatMapMany(pageable -> documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(knowledgeBaseId, pageable))
        .flatMap(this::toEnrichedDto)
        .doOnComplete(() -> log.info("文档列表查询完成: knowledgeBaseId={}", knowledgeBaseId))
        .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<DocumentDto> getAllDocuments(Integer page, Integer size) {
        return Mono.fromCallable(() -> {
            final int finalPage = page != null && page >= 0 ? page : 0;
            final int finalSize = size != null && size > 0 && size <= 200 ? size : 20;
            return PageRequest.of(finalPage, finalSize);
        })
        .flatMapMany(documentRepository::findAllByOrderByUpdatedAtDesc)
        .flatMap(this::toEnrichedDto)
        .doOnComplete(() -> log.info("全站文档列表查询完成: page={}, size={}", page, size))
        .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<DocumentDto> getDocumentsByUploaderId(Long uploaderId, Integer page, Integer size) {
        return Mono.fromCallable(() -> {
            final int finalPage = page != null && page >= 0 ? page : 0;
            final int finalSize = size != null && size > 0 && size <= 100 ? size : 10;
            return PageRequest.of(finalPage, finalSize);
        })
        .flatMapMany(pageable -> documentRepository.findByUploaderIdOrderByCreatedAtDesc(uploaderId, pageable))
        .flatMap(this::toEnrichedDto)
        .doOnComplete(() -> log.info("按上传用户查询文档完成: uploaderId={}, page={}, size={}", uploaderId, page, size))
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
                    .then(syncKnowledgeBaseDocumentCount(knowledgeBaseId)
                            .onErrorResume(error -> {
                                log.warn("同步知识库文档数量失败: knowledgeBaseId={}, error={}",
                                        knowledgeBaseId, error.getMessage());
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
                log.info("重新解析文档: id={}, fileName={}", id, document.getFileName());
                
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
            .doOnError(error -> log.error("重新解析文档失败: id={}, error={}", id, error.getMessage()))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<DocumentDto> reEmbedDocument(Long id, Long requesterUserId, boolean isAdmin) {
        return getAuthorizedDocument(id, requesterUserId, isAdmin, true)
            .flatMap(document -> {
                log.info("重新向量化文档: id={}, fileName={}", id, document.getFileName());
                
                return documentIngestionService.reEmbedDocument(id, document.getKnowledgeBaseId())
                    .map(task -> {
                        log.info("重新向量化已受理: documentId={}, firstStage={}, action={}",
                                task.documentId(), task.firstStage(), task.action());
                        return document;
                    });
            })
            .map(this::toDto)
            .doOnSuccess(dto -> log.info("重新向量化文档完成: id={}", id))
            .doOnError(error -> log.error("重新向量化文档失败: id={}, error={}", id, error.getMessage()))
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
        
        log.info("文件类型检查: fileName={}, extension={}, supportedTypes={}, isSupported={}", 
                fileName, extension, supportedList, isSupported);
        
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
                    log.warn("补全文档知识库名称失败: documentId={}, knowledgeBaseId={}, error={}",
                            entity.getId(), entity.getKnowledgeBaseId(), error.getMessage());
                    return Mono.empty();
                })
                .defaultIfEmpty("未知知识库");

        Mono<String> uploaderNameMono = Mono.justOrEmpty(entity.getUploaderId())
                .flatMap(userRepository::findById)
                .map(User::getUsername)
                .onErrorResume(error -> {
                    log.warn("补全文档上传者名称失败: documentId={}, uploaderId={}, error={}",
                            entity.getId(), entity.getUploaderId(), error.getMessage());
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
    
    /**
     * 动态计算文档是否已向量化
     * 基于实际的文档块向量化状态进行判断
     */
    private Boolean calculateIsEmbedded(KnowledgeDocument document) {
        // 如果文档还没有解析或没有分块，则肯定未向量化
        if (document.getChunkCount() == null || document.getChunkCount() <= 0) {
            return false;
        }
        
        // 如果向量化状态为COMPLETED，则认为已向量化
        if ("COMPLETED".equals(document.getEmbeddingStatus())) {
            return true;
        }
        
        // 如果向量化状态为FAILED，则认为未向量化
        if ("FAILED".equals(document.getEmbeddingStatus())) {
            return false;
        }
        
        // 其他情况（PENDING, PROCESSING）使用数据库中的值作为默认值
        return document.getIsEmbedded() != null ? document.getIsEmbedded() : false;
    }
    
    /**
     * 上传文件到OSS
     */
    private Mono<OssService.OssFileInfo> uploadToOss(MultipartFile file, Long knowledgeBaseId) {
        return Mono.fromCallable(() -> {
            try {
                return file.getInputStream();
            } catch (Exception e) {
                throw new RuntimeException("OSS上传失败: " + e.getMessage(), e);
            }
        })
        .flatMap(inputStream -> {
            return ossService.uploadKnowledgeDocument(
                knowledgeBaseId,
                file.getOriginalFilename(),
                inputStream,
                file.getSize(),
                file.getContentType()
            );
        })
        .subscribeOn(Schedulers.boundedElastic());
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
        .subscribeOn(Schedulers.boundedElastic());
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

    private Mono<Void> syncKnowledgeBaseDocumentCount(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return Mono.empty();
        }

        return documentRepository.countByKnowledgeBaseId(knowledgeBaseId)
                .flatMap(count -> knowledgeBaseService.updateDocumentCount(knowledgeBaseId, count.intValue()))
                .then();
    }
}
