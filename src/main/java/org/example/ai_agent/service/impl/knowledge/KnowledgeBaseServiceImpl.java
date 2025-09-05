package org.example.ai_agent.service.impl.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseCreateDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.entity.KnowledgeBase;
import org.example.ai_agent.repository.knowledge.KnowledgeBaseRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

/**
 * 知识库服务实现类
 * <p>
 * 基于LangChain4j 1.0.1的知识库管理服务实现
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
@Slf4j
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private KnowledgeDocumentRepository documentRepository;
    
    @Value("${knowledge-base.embedding.default-model:qwen-embedding-8b}")
    private String defaultEmbeddingModel;
    
    @Value("${knowledge-base.embedding.index-prefix:kb_}")
    private String embeddingIndexPrefix;
    
    @Override
    public Mono<KnowledgeBaseDto> createKnowledgeBase(KnowledgeBaseCreateDto createDto, Long creatorId) {
        return Mono.fromCallable(() -> {
            log.info("开始创建知识库: name={}, creatorId={}", createDto.getName(), creatorId);
            
            if (createDto.getName() == null || createDto.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("知识库名称不能为空");
            }
            
            if (creatorId == null) {
                throw new IllegalArgumentException("创建者ID不能为空");
            }
            
            return toEntity(createDto, creatorId);
        })
        .flatMap(entity -> {
            // 检查名称是否重复
            return knowledgeBaseRepository.findByName(entity.getName())
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("知识库名称已存在: " + entity.getName()));
                    }
                    return knowledgeBaseRepository.save(entity);
                });
        })
        .map(this::toDto)
        .doOnSuccess(dto -> log.info("知识库创建成功: id={}, name={}", dto.getId(), dto.getName()))
        .doOnError(error -> log.error("知识库创建失败: name={}, error={}", createDto.getName(), error.getMessage()))
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<KnowledgeBaseDto> getKnowledgeBases(Boolean publicOnly, Integer page, Integer size) {
        return Mono.fromCallable(() -> {
            log.info("查询知识库列表: publicOnly={}, page={}, size={}", publicOnly, page, size);
            
            final int finalPage = page != null && page >= 0 ? page : 0;
            final int finalSize = size != null && size > 0 && size <= 100 ? size : 10;
            
            Pageable pageable = PageRequest.of(finalPage, finalSize);
            
            if (publicOnly != null && publicOnly) {
                return knowledgeBaseRepository.findByIsPublicAndStatusOrderByCreatedAtDesc(true, "ACTIVE", pageable);
            } else {
                return knowledgeBaseRepository.findByStatusOrderByCreatedAtDesc("ACTIVE", pageable);
            }
        })
        .flatMapMany(flux -> flux)
        .flatMap(this::toDtoWithDocumentCount) // 使用新的方法，动态计算文档数量
        .doOnComplete(() -> log.info("知识库列表查询完成"))
        .doOnError(error -> log.error("知识库列表查询失败: error={}", error.getMessage()))
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<KnowledgeBaseDto> getKnowledgeBase(Long id) {
        return knowledgeBaseRepository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("知识库不存在: id=" + id)))
            .map(this::toDto)
            .doOnSuccess(dto -> log.info("知识库详情查询成功: id={}, name={}", id, dto.getName()))
            .doOnError(error -> log.error("知识库详情查询失败: id={}, error={}", id, error.getMessage()))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<KnowledgeBaseDto> updateKnowledgeBase(Long id, KnowledgeBaseCreateDto updateDto) {
        return knowledgeBaseRepository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("知识库不存在: id=" + id)))
            .flatMap(existingKB -> {
                // 检查名称是否重复（排除当前知识库）
                if (!existingKB.getName().equals(updateDto.getName())) {
                    return knowledgeBaseRepository.findByName(updateDto.getName())
                        .hasElement()
                        .flatMap(exists -> {
                            if (exists) {
                                return Mono.error(new IllegalArgumentException("知识库名称已存在: " + updateDto.getName()));
                            }
                            return updateEntity(existingKB, updateDto);
                        });
                } else {
                    return updateEntity(existingKB, updateDto);
                }
            })
            .flatMap(knowledgeBaseRepository::save)
            .map(this::toDto)
            .doOnSuccess(dto -> log.info("知识库更新成功: id={}, name={}", id, dto.getName()))
            .doOnError(error -> log.error("知识库更新失败: id={}, error={}", id, error.getMessage()))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Void> deleteKnowledgeBase(Long id) {
        return knowledgeBaseRepository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("知识库不存在: id=" + id)))
            .flatMap(kb -> {
                // 软删除：更新状态为DELETED
                kb.setStatus("DELETED");
                kb.setUpdatedAt(LocalDateTime.now());
                return knowledgeBaseRepository.save(kb);
            })
            .then()
            .doOnSuccess(v -> log.info("知识库删除成功: id={}", id))
            .doOnError(error -> log.error("知识库删除失败: id={}, error={}", id, error.getMessage()))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<Boolean> existsById(Long id) {
        return knowledgeBaseRepository.existsById(id)
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<KnowledgeBase> updateDocumentCount(Long id, Integer documentCount) {
        return knowledgeBaseRepository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("知识库不存在: id=" + id)))
            .flatMap(kb -> {
                kb.setDocumentCount(documentCount);
                kb.setUpdatedAt(LocalDateTime.now());
                return knowledgeBaseRepository.save(kb);
            })
            .doOnSuccess(kb -> log.info("知识库文档数量更新成功: id={}, documentCount={}", id, documentCount))
            .doOnError(error -> log.error("知识库文档数量更新失败: id={}, error={}", id, error.getMessage()))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public KnowledgeBaseDto toDto(KnowledgeBase entity) {
        if (entity == null) {
            return null;
        }
        
        KnowledgeBaseDto dto = new KnowledgeBaseDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setCreatorId(entity.getCreatorId());
        dto.setIsPublic(entity.getIsPublic());
        dto.setStatus(entity.getStatus());
        dto.setDocumentCount(entity.getDocumentCount());
        dto.setVectorDimension(entity.getVectorDimension());
        dto.setEmbeddingModel(entity.getEmbeddingModel());
        dto.setIndexName(entity.getIndexName());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        
        return dto;
    }
    
    /**
     * 转换实体为DTO（动态计算文档数量）
     */
    private Mono<KnowledgeBaseDto> toDtoWithDocumentCount(KnowledgeBase entity) {
        if (entity == null) {
            return Mono.empty();
        }
        
        log.info("动态计算知识库文档数量: knowledgeBaseId={}", entity.getId());
        
        // 动态查询实际文档数量
        return documentRepository.countByKnowledgeBaseId(entity.getId())
            .doOnNext(count -> log.info("知识库 {} 的实际文档数量: {}", entity.getId(), count))
            .map(count -> {
                KnowledgeBaseDto dto = new KnowledgeBaseDto();
                dto.setId(entity.getId());
                dto.setName(entity.getName());
                dto.setDescription(entity.getDescription());
                dto.setCreatorId(entity.getCreatorId());
                dto.setIsPublic(entity.getIsPublic());
                dto.setStatus(entity.getStatus());
                dto.setDocumentCount(count.intValue()); // 使用实际统计的文档数量
                dto.setVectorDimension(entity.getVectorDimension());
                dto.setEmbeddingModel(entity.getEmbeddingModel());
                dto.setIndexName(entity.getIndexName());
                dto.setCreatedAt(entity.getCreatedAt());
                dto.setUpdatedAt(entity.getUpdatedAt());
                
                log.info("返回知识库 {} 的DTO，文档数量: {}", entity.getId(), count.intValue());
                return dto;
            })
            .doOnError(error -> log.error("查询知识库 {} 文档数量失败: {}", entity.getId(), error.getMessage()))
            .defaultIfEmpty(toDto(entity)); // 如果查询失败，使用原始方法
    }
    
    @Override
    public KnowledgeBase toEntity(KnowledgeBaseCreateDto createDto, Long creatorId) {
        if (createDto == null) {
            return null;
        }
        
        KnowledgeBase entity = new KnowledgeBase();
        entity.setName(createDto.getName());
        entity.setDescription(createDto.getDescription());
        entity.setCreatorId(creatorId);
        entity.setIsPublic(createDto.getIsPublic() != null ? createDto.getIsPublic() : false);
        entity.setStatus("ACTIVE");
        entity.setDocumentCount(0);
        
        // 设置默认向量化模型
        String embeddingModel = createDto.getEmbeddingModel() != null ? 
            createDto.getEmbeddingModel() : defaultEmbeddingModel;
        entity.setEmbeddingModel(embeddingModel);
        
        // 生成索引名称
        String indexName = embeddingIndexPrefix + System.currentTimeMillis();
        entity.setIndexName(indexName);
        
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        
        return entity;
    }
    
    /**
     * 更新实体属性
     */
    private Mono<KnowledgeBase> updateEntity(KnowledgeBase entity, KnowledgeBaseCreateDto updateDto) {
        return Mono.fromCallable(() -> {
            if (updateDto.getName() != null) {
                entity.setName(updateDto.getName());
            }
            if (updateDto.getDescription() != null) {
                entity.setDescription(updateDto.getDescription());
            }
            if (updateDto.getIsPublic() != null) {
                entity.setIsPublic(updateDto.getIsPublic());
            }
            if (updateDto.getEmbeddingModel() != null) {
                entity.setEmbeddingModel(updateDto.getEmbeddingModel());
            }
            
            entity.setUpdatedAt(LocalDateTime.now());
            return entity;
        });
    }
}