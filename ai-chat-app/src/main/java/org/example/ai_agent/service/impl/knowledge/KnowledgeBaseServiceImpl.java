package org.example.ai_agent.service.impl.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.admin.AdminPageDto;
import org.example.ai_agent.dto.knowledge.AdminKnowledgeBaseSummaryProjection;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseCreateDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDocumentStatsProjection;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseStatsDto;
import org.example.ai_agent.entity.KnowledgeBase;
import org.example.ai_agent.repository.knowledge.KnowledgeBaseRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.KnowledgeChunkSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 知识库服务实现类
 * <p>
 * 基于LangChain4j 1.0.1的知识库管理服务实现
 * </p>
 *
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Service
public class  KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    private final KnowledgeDocumentRepository documentRepository;

    private final KnowledgeAssetCleanupService knowledgeAssetCleanupService;

    private final KnowledgeChunkSearchService knowledgeChunkSearchService;

    @Value("${knowledge-base.embedding.default-model:qwen-embedding-8b}")
    private String defaultEmbeddingModel;

    @Value("${knowledge-base.embedding.index-prefix:kb_}")
    private String embeddingIndexPrefix;

    @Value("${knowledge-base.list.slow-threshold-ms:500}")
    private long knowledgeBaseListSlowThresholdMs;

    public KnowledgeBaseServiceImpl(KnowledgeBaseRepository knowledgeBaseRepository,
                                    KnowledgeDocumentRepository documentRepository,
                                    KnowledgeAssetCleanupService knowledgeAssetCleanupService,
                                    KnowledgeChunkSearchService knowledgeChunkSearchService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.knowledgeAssetCleanupService = knowledgeAssetCleanupService;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
    }

    @Override
    public Mono<KnowledgeBaseDto> createKnowledgeBase(KnowledgeBaseCreateDto createDto, Long creatorId) {
        return Mono.fromCallable(() -> {
            int nameLength = createDto.getName() == null ? 0 : createDto.getName().length();
            log.info("开始创建知识库: nameLength={}", nameLength);

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
        .doOnSuccess(dto -> log.info("知识库创建成功: id={}", dto.getId()))
        .doOnError(error -> log.error("知识库创建失败: errorType={}", error.getClass().getSimpleName()))
        .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<KnowledgeBaseDto> getKnowledgeBases(Long creatorId, Boolean publicOnly, Integer page, Integer size) {
        return Flux.defer(() -> {
            long totalStartNanos = System.nanoTime();
            AtomicLong queryStartNanos = new AtomicLong();
            log.info("查询知识库列表: publicOnly={}, page={}, size={}", publicOnly, page, size);

            final int finalPage = page != null && page >= 0 ? page : 0;
            final int finalSize = size != null && size > 0 && size <= 100 ? size : 10;

            Pageable pageable = PageRequest.of(finalPage, finalSize);
            Flux<KnowledgeBase> query;

            if (publicOnly != null && publicOnly) {
                query = knowledgeBaseRepository.findByIsPublicAndStatusOrderByCreatedAtDesc(true, "ACTIVE", pageable);
            } else if (creatorId != null) {
                // Return user's private bases + all public bases
                query = knowledgeBaseRepository.findByCreatorIdOrPublicAndStatus(
                        creatorId, true, "ACTIVE", finalSize, pageable.getOffset());
            } else {
                // If no user is logged in, only return public bases
                query = knowledgeBaseRepository.findByIsPublicAndStatusOrderByCreatedAtDesc(true, "ACTIVE", pageable);
            }

            return query
                    .doOnSubscribe(subscription -> queryStartNanos.set(System.nanoTime()))
                    .map(this::toDto)
                    .collectList()
                    .flatMapMany(items -> {
                        long totalMs = elapsedMs(totalStartNanos);
                        long queryMs = queryStartNanos.get() > 0 ? elapsedMs(queryStartNanos.get()) : -1;
                        if (totalMs > knowledgeBaseListSlowThresholdMs) {
                            log.warn("知识库列表慢查询: publicOnly={}, page={}, size={}, resultCount={}, queryMs={}, totalMs={}",
                                    publicOnly, finalPage, finalSize, items.size(), queryMs, totalMs);
                        } else {
                            log.info("知识库列表查询完成: resultCount={}, queryMs={}ms, totalMs={}ms",
                                    items.size(), queryMs, totalMs);
                        }
                        return Flux.fromIterable(items);
                    });
        }).doOnError(error -> log.error("知识库列表查询失败: errorType={}", error.getClass().getSimpleName()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    @Override
    public Flux<KnowledgeBaseDto> getAllKnowledgeBases(Integer page, Integer size) {
        return Mono.fromCallable(() -> {
            log.info("管理员查询全站知识库列表: page={}, size={}", page, size);

            final int finalPage = page != null && page >= 0 ? page : 0;
            final int finalSize = size != null && size > 0 && size <= 100 ? size : 20;

            Pageable pageable = PageRequest.of(finalPage, finalSize);
            return knowledgeBaseRepository.findByStatusOrderByCreatedAtDesc("ACTIVE", pageable);
        })
        .flatMapMany(flux -> flux)
        .map(this::toDto)
        .doOnComplete(() -> log.info("管理员全站知识库列表查询完成"))
        .doOnError(error -> log.error("管理员全站知识库列表查询失败: errorType={}", error.getClass().getSimpleName()))
        .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<AdminPageDto<KnowledgeBaseDto>> getAllKnowledgeBasesPage(Integer page, Integer size, String keyword) {
        int finalPage = page != null && page >= 0 ? page : 0;
        int finalSize = size != null && size > 0 && size <= 100 ? size : 10;
        String finalKeyword = normalizeFilter(keyword);
        long offset = (long) finalPage * finalSize;

        return Mono.zip(
                knowledgeBaseRepository.findAdminPage(finalKeyword, finalSize, offset)
                        .map(this::toDto)
                        .collectList(),
                knowledgeBaseRepository.countAdminPage(finalKeyword),
                knowledgeBaseRepository.summarizeForAdmin()
                        .defaultIfEmpty(new AdminKnowledgeBaseSummaryProjection())
        ).map(tuple -> new AdminPageDto<>(
                tuple.getT1(),
                tuple.getT2(),
                toAdminSummary(tuple.getT3())
        )).doOnSuccess(result -> log.info("管理员知识库分页查询完成: page={}, size={}, total={}",
                finalPage, finalSize, result.getTotal()));
    }

    @Override
    public Flux<KnowledgeBaseDto> getKnowledgeBasesByCreatorId(Long creatorId, Integer page, Integer size) {
        return Mono.fromCallable(() -> {
            log.info("管理员查询用户知识库列表: page={}, size={}", page, size);

            final int finalPage = page != null && page >= 0 ? page : 0;
            final int finalSize = size != null && size > 0 && size <= 100 ? size : 10;

            Pageable pageable = PageRequest.of(finalPage, finalSize);
            return knowledgeBaseRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId, pageable);
        })
        .flatMapMany(flux -> flux)
        .map(this::toDto)
        .doOnComplete(() -> log.info("管理员用户知识库列表查询完成"))
        .doOnError(error -> log.error("管理员用户知识库列表查询失败: errorType={}",
                error.getClass().getSimpleName()))
        .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<KnowledgeBaseDto> getKnowledgeBase(Long id) {
        return knowledgeBaseRepository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("知识库不存在: id=" + id)))
            .map(this::toDto)
            .doOnSuccess(dto -> log.info("知识库详情查询成功: id={}", id))
            .doOnError(error -> log.error("知识库详情查询失败: id={}, errorType={}", id, error.getClass().getSimpleName()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<KnowledgeBaseDto> getKnowledgeBase(Long id, Long requesterUserId, boolean isAdmin) {
        return getAuthorizedKnowledgeBase(id, requesterUserId, isAdmin, false)
            .map(this::toDto)
            .doOnSuccess(dto -> log.info("知识库访问鉴权通过: id={}, isAdmin={}", id, isAdmin))
            .doOnError(error -> log.warn("知识库访问鉴权失败: id={}, isAdmin={}, errorType={}",
                    id, isAdmin, error.getClass().getSimpleName()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<KnowledgeBaseDto> updateKnowledgeBase(Long id, KnowledgeBaseCreateDto updateDto,
                                                      Long requesterUserId, boolean isAdmin) {
        return getAuthorizedKnowledgeBase(id, requesterUserId, isAdmin, true)
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
            .flatMap(saved -> knowledgeChunkSearchService.refreshKnowledgeBaseChunks(saved.getId()).thenReturn(saved))
            .map(this::toDto)
            .doOnSuccess(dto -> log.info("知识库更新成功: id={}", id))
            .doOnError(error -> log.error("知识库更新失败: id={}, errorType={}", id, error.getClass().getSimpleName()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteKnowledgeBase(Long id, Long requesterUserId, boolean isAdmin) {
        return getAuthorizedKnowledgeBase(id, requesterUserId, isAdmin, true)
            .flatMap(kb -> {
                return knowledgeAssetCleanupService.deleteKnowledgeBaseAssets(kb)
                        .then(knowledgeBaseRepository.delete(kb));
            })
            .then()
            .doOnSuccess(v -> log.info("知识库删除成功: id={}", id))
            .doOnError(error -> log.error("知识库删除失败: id={}, errorType={}", id, error.getClass().getSimpleName()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> existsById(Long id) {
        return knowledgeBaseRepository.existsById(id)
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<KnowledgeBase> getAuthorizedKnowledgeBase(Long id, Long requesterUserId,
                                                          boolean isAdmin, boolean requireWriteAccess) {
        return knowledgeBaseRepository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("知识库不存在: id=" + id)))
            .flatMap(knowledgeBase -> {
                if (isAdmin) {
                    return Mono.just(knowledgeBase);
                }

                if (requesterUserId == null || requesterUserId <= 0) {
                    return Mono.error(new SecurityException("当前用户无权访问该知识库"));
                }

                boolean owner = knowledgeBase.getCreatorId() != null
                        && knowledgeBase.getCreatorId().equals(requesterUserId);
                if (owner) {
                    return Mono.just(knowledgeBase);
                }

                if (!requireWriteAccess && Boolean.TRUE.equals(knowledgeBase.getIsPublic())) {
                    return Mono.just(knowledgeBase);
                }

                String message = requireWriteAccess ? "当前用户无权修改该知识库" : "当前用户无权访问该知识库";
                return Mono.error(new SecurityException(message));
            });
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
            .doOnError(error -> log.error("知识库文档数量更新失败: id={}, errorType={}", id, error.getClass().getSimpleName()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<KnowledgeBaseStatsDto> getKnowledgeBaseStats(Long id, Long requesterUserId, boolean isAdmin) {
        return getAuthorizedKnowledgeBase(id, requesterUserId, isAdmin, false)
            .flatMap(knowledgeBase -> documentRepository.summarizeByKnowledgeBaseId(id)
                    .defaultIfEmpty(new KnowledgeBaseDocumentStatsProjection())
                    .map(stats -> toStatsDto(knowledgeBase, stats)))
            .doOnSuccess(stats -> log.info("知识库统计查询成功: id={}, documentCount={}",
                    id, stats.getDocumentCount()))
            .doOnError(error -> log.warn("知识库统计查询失败: id={}, errorType={}", id, error.getClass().getSimpleName()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> adjustDocumentCount(Long id, Integer delta) {
        if (id == null || delta == null || delta == 0) {
            return Mono.empty();
        }

        return knowledgeBaseRepository.adjustDocumentCount(id, delta)
            .flatMap(rowsUpdated -> rowsUpdated > 0
                    ? Mono.<Void>empty()
                    : Mono.error(new IllegalArgumentException("知识库不存在: id=" + id)))
            .doOnSuccess(unused -> log.info("知识库文档数量原子调整成功: id={}, delta={}", id, delta))
            .doOnError(error -> log.error("知识库文档数量原子调整失败: id={}, delta={}, errorType={}",
                    id, delta, error.getClass().getSimpleName()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public KnowledgeBaseDto toDto(KnowledgeBase entity) {
        if (entity == null) {
            throw new IllegalArgumentException("知识库实体不能为空");
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

    private KnowledgeBaseStatsDto toStatsDto(KnowledgeBase knowledgeBase,
                                              KnowledgeBaseDocumentStatsProjection stats) {
        KnowledgeBaseStatsDto dto = new KnowledgeBaseStatsDto();
        dto.setKnowledgeBaseId(knowledgeBase.getId());
        dto.setKnowledgeBaseName(knowledgeBase.getName());
        dto.setDocumentCount(defaultCount(stats.getDocumentCount()));
        dto.setParsedDocumentCount(defaultCount(stats.getParsedDocumentCount()));
        dto.setFailedDocumentCount(defaultCount(stats.getFailedDocumentCount()));
        dto.setEmbeddedDocumentCount(defaultCount(stats.getEmbeddedDocumentCount()));
        return dto;
    }

    private long defaultCount(Long value) {
        return value != null ? value : 0L;
    }

    private Map<String, Object> toAdminSummary(AdminKnowledgeBaseSummaryProjection projection) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("knowledgeBaseCount", defaultCount(projection.getKnowledgeBaseCount()));
        summary.put("documentCount", defaultCount(projection.getDocumentCount()));
        summary.put("publicKnowledgeBaseCount", defaultCount(projection.getPublicKnowledgeBaseCount()));
        return summary;
    }

    private String normalizeFilter(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }



    @Override
    public KnowledgeBase toEntity(KnowledgeBaseCreateDto createDto, Long creatorId) {
        if (createDto == null) {
            throw new IllegalArgumentException("知识库创建请求不能为空");
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
