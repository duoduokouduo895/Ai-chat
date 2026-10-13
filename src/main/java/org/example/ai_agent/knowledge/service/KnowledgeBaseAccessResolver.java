package org.example.ai_agent.knowledge.service;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.knowledge.entity.KnowledgeBase;
import org.example.ai_agent.knowledge.repository.KnowledgeBaseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Component
public class KnowledgeBaseAccessResolver {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public KnowledgeBaseAccessResolver(KnowledgeBaseRepository knowledgeBaseRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    public Flux<KnowledgeBase> resolveReadable(List<Long> knowledgeBaseIds,
                                               Long requesterUserId,
                                               boolean isAdmin) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return knowledgeBaseRepository
                    .findByIsPublicAndStatusOrderByCreatedAtDesc(true, "ACTIVE", PageRequest.of(0, 5))
                    .doOnComplete(() -> log.debug("未指定知识库，仅使用最近 5 个公开活跃知识库进行 RAG"));
        }

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(knowledgeBaseIds);
        if (uniqueIds.contains(null) || uniqueIds.stream().anyMatch(id -> id <= 0)) {
            return Flux.error(new IllegalArgumentException("知识库ID必须为正数"));
        }

        return knowledgeBaseRepository.findAllById(uniqueIds)
                .collectList()
                .flatMapMany(knowledgeBases -> {
                    if (knowledgeBases.size() != uniqueIds.size()) {
                        return Flux.error(new IllegalArgumentException("部分知识库不存在"));
                    }
                    return Flux.fromIterable(knowledgeBases);
                })
                .concatMap(knowledgeBase -> authorizeRead(knowledgeBase, requesterUserId, isAdmin));
    }

    public Mono<KnowledgeBase> authorizeRead(KnowledgeBase knowledgeBase,
                                             Long requesterUserId,
                                             boolean isAdmin) {
        if (!"ACTIVE".equalsIgnoreCase(knowledgeBase.getStatus())
                || !"ACTIVE".equalsIgnoreCase(knowledgeBase.getDeletionStatus())) {
            return Mono.error(new SecurityException("当前知识库不可用于检索"));
        }
        if (isAdmin) {
            return Mono.just(knowledgeBase);
        }
        if (requesterUserId == null || requesterUserId <= 0) {
            return Mono.error(new SecurityException("当前用户无权访问该知识库"));
        }
        boolean isOwner = requesterUserId.equals(knowledgeBase.getCreatorId());
        if (isOwner || Boolean.TRUE.equals(knowledgeBase.getIsPublic())) {
            return Mono.just(knowledgeBase);
        }
        return Mono.error(new SecurityException("当前用户无权访问该知识库"));
    }
}
