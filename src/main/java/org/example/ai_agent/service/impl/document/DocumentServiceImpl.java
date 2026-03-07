package org.example.ai_agent.service.impl.document;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.document.DocumentDto;
import org.example.ai_agent.entity.KnowledgeDocument;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.DocumentProcessingService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.OssService;
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
    private DocumentProcessingService documentProcessingService;
    
    @Value("${knowledge-base.document.supported-types:pdf,docx,doc,txt,md,pptx,xlsx}")
    private String[] supportedTypes;
    
    @Override
    public Mono<DocumentDto> uploadDocument(MultipartFile file, Long knowledgeBaseId, String parseMode, Boolean enableVectorization, Long uploaderId) {
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
            
            return file;
        })
        .flatMap(validatedFile -> {
            // 检查知识库是否存在
            return knowledgeBaseService.existsById(knowledgeBaseId)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new IllegalArgumentException("知识库不存在: id=" + knowledgeBaseId));
                    }
                    return uploadToOss(validatedFile, knowledgeBaseId);
                });
        })
        .flatMap(ossFileInfo -> {
            // 创建文档记录
            return saveDocumentRecord(file, knowledgeBaseId, parseMode, enableVectorization, 
                    ossFileInfo.ossPath(), ossFileInfo.url(), uploaderId);
        })
        .flatMap(document -> {
            // 启动自动处理流水线
            return documentProcessingService.startDocumentProcessing(
                    document.getId(), knowledgeBaseId, parseMode, enableVectorization)
                .map(processingStarted -> {
                    if (processingStarted) {
                        log.info("文档处理流水线启动成功: documentId={}", document.getId());
                    } else {
                        log.warn("文档处理流水线启动失败: documentId={}", document.getId());
                    }
                    return document;
                });
        })
        .map(this::toDto)
        .doOnSuccess(dto -> log.info("文档上传成功: id={}, fileName={}", dto.getId(), dto.getFileName()))
        .doOnError(error -> log.error("文档上传失败: fileName={}, error={}", file.getOriginalFilename(), error.getMessage()))
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<DocumentDto> getDocuments(Long knowledgeBaseId, Integer page, Integer size) {
        return Mono.fromCallable(() -> {
            final int finalPage = page != null && page >= 0 ? page : 0;
            final int finalSize = size != null && size > 0 && size <= 100 ? size : 10;
            return PageRequest.of(finalPage, finalSize);
        })
        .flatMapMany(pageable -> documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(knowledgeBaseId, pageable))
        .map(this::toDto)
        .doOnComplete(() -> log.info("文档列表查询完成: knowledgeBaseId={}", knowledgeBaseId))
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<DocumentDto> getDocument(Long id) {
        return documentRepository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("文档不存在: id=" + id)))
            .map(this::toDto)
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Void> deleteDocument(Long id) {
        return documentRepository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("文档不存在: id=" + id)))
            .flatMap(document -> {
                // 删除OSS文件
                return ossService.deleteFile(document.getOssPath())
                    .then(documentRepository.deleteById(id));
            })
            .doOnSuccess(v -> log.info("文档删除成功: id={}", id))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<DocumentDto> reparseDocument(Long id) {
        return documentRepository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("文档不存在: id=" + id)))
            .flatMap(document -> {
                log.info("重新解析文档: id={}, fileName={}", id, document.getFileName());
                
                // 使用默认解析模式
                String parseMode = "auto";
                
                return documentProcessingService.reparseDocument(id, parseMode)
                    .map(success -> {
                        if (success) {
                            log.info("重新解析启动成功: documentId={}", id);
                        } else {
                            log.warn("重新解析启动失败: documentId={}", id);
                        }
                        return document;
                    });
            })
            .map(this::toDto)
            .doOnSuccess(dto -> log.info("重新解析文档完成: id={}", id))
            .doOnError(error -> log.error("重新解析文档失败: id={}, error={}", id, error.getMessage()))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<DocumentDto> reEmbedDocument(Long id) {
        return documentRepository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("文档不存在: id=" + id)))
            .flatMap(document -> {
                log.info("重新向量化文档: id={}, fileName={}", id, document.getFileName());
                
                return documentProcessingService.reEmbedDocument(id)
                    .map(success -> {
                        if (success) {
                            log.info("重新向量化启动成功: documentId={}", id);
                        } else {
                            log.warn("重新向量化启动失败: documentId={}", id);
                        }
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
            document.setUploaderId(uploaderId != null ? uploaderId : 1L); // 使用传入的用户ID，如果为空则使用默认值
            
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
}