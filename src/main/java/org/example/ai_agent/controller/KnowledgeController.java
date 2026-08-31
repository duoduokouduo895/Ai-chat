package org.example.ai_agent.controller;

import jakarta.validation.Valid;
import org.example.ai_agent.controller.support.KnowledgeAccessContextResolver;
import org.example.ai_agent.dto.admin.AdminPageDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseCreateDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseStatsDto;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchDto;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchResultDto;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.KnowledgeChunkSearchService;
import org.example.ai_agent.service.RagService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** HTTP seam for knowledge-base management and retrieval. */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final RagService ragService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeChunkSearchService knowledgeChunkSearchService;
    private final KnowledgeAccessContextResolver accessContextResolver;

    public KnowledgeController(RagService ragService,
                               KnowledgeBaseService knowledgeBaseService,
                               KnowledgeChunkSearchService knowledgeChunkSearchService,
                               KnowledgeAccessContextResolver accessContextResolver) {
        this.ragService = ragService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
        this.accessContextResolver = accessContextResolver;
    }

    @PostMapping("/search")
    public Mono<KnowledgeSearchResultDto> searchKnowledge(@Valid @RequestBody KnowledgeSearchDto searchRequest,
                                                           Authentication authentication) {
        return accessContextResolver.resolve(authentication)
                .flatMap(access -> ragService.searchKnowledge(searchRequest, access.userId(), access.isAdmin()));
    }

    @PostMapping("/bases")
    public Mono<KnowledgeBaseDto> createKnowledgeBase(@Valid @RequestBody KnowledgeBaseCreateDto createRequest,
                                                       Authentication authentication) {
        return accessContextResolver.requireUser(authentication)
                .flatMap(access -> knowledgeBaseService.createKnowledgeBase(createRequest, access.userId()));
    }

    @PostMapping("/admin/bases")
    public Mono<KnowledgeBaseDto> createKnowledgeBaseForAdmin(@Valid @RequestBody KnowledgeBaseCreateDto createRequest,
                                                               @RequestParam Long creatorId,
                                                               Authentication authentication) {
        return accessContextResolver.requireAdmin(authentication)
                .then(accessContextResolver.requireExistingUserId(creatorId, "知识库归属用户不存在"))
                .flatMap(validCreatorId -> knowledgeBaseService.createKnowledgeBase(createRequest, validCreatorId));
    }

    @GetMapping("/bases")
    public Flux<KnowledgeBaseDto> getKnowledgeBases(@RequestParam(required = false) Boolean publicOnly,
                                                     @RequestParam(defaultValue = "0") Integer page,
                                                     @RequestParam(defaultValue = "10") Integer size,
                                                     Authentication authentication) {
        return accessContextResolver.resolve(authentication)
                .flatMapMany(access -> knowledgeBaseService.getKnowledgeBases(
                        access.isAdmin() ? null : access.userId(), publicOnly, page, size));
    }

    @GetMapping("/admin/bases")
    public Flux<KnowledgeBaseDto> getAllKnowledgeBasesForAdmin(@RequestParam(defaultValue = "0") Integer page,
                                                                @RequestParam(defaultValue = "100") Integer size) {
        return knowledgeBaseService.getAllKnowledgeBases(page, size);
    }

    @GetMapping("/admin/bases/page")
    public Mono<AdminPageDto<KnowledgeBaseDto>> getAllKnowledgeBasesPageForAdmin(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        return knowledgeBaseService.getAllKnowledgeBasesPage(page, size, keyword);
    }

    @PostMapping("/admin/chunk-search/rebuild")
    public Mono<Void> rebuildKnowledgeChunkSearchIndex(Authentication authentication) {
        return accessContextResolver.requireAdmin(authentication)
                .then(knowledgeChunkSearchService.rebuildAllChunks());
    }

    @GetMapping("/bases/{id}")
    public Mono<KnowledgeBaseDto> getKnowledgeBase(@PathVariable Long id, Authentication authentication) {
        return accessContextResolver.resolve(authentication)
                .flatMap(access -> knowledgeBaseService.getKnowledgeBase(id, access.userId(), access.isAdmin()));
    }

    @GetMapping("/bases/{id}/stats")
    public Mono<KnowledgeBaseStatsDto> getKnowledgeBaseStats(@PathVariable Long id, Authentication authentication) {
        return accessContextResolver.resolve(authentication)
                .flatMap(access -> knowledgeBaseService.getKnowledgeBaseStats(id, access.userId(), access.isAdmin()));
    }

    @PutMapping("/bases/{id}")
    public Mono<KnowledgeBaseDto> updateKnowledgeBase(@PathVariable Long id,
                                                       @Valid @RequestBody KnowledgeBaseCreateDto updateRequest,
                                                       Authentication authentication) {
        return accessContextResolver.resolve(authentication)
                .flatMap(access -> knowledgeBaseService.updateKnowledgeBase(
                        id, updateRequest, access.userId(), access.isAdmin()));
    }

    @DeleteMapping("/bases/{id}")
    public Mono<Void> deleteKnowledgeBase(@PathVariable Long id, Authentication authentication) {
        return accessContextResolver.resolve(authentication)
                .flatMap(access -> knowledgeBaseService.deleteKnowledgeBase(id, access.userId(), access.isAdmin()));
    }

    @GetMapping("/bases/{id}/exists")
    public Mono<Boolean> checkKnowledgeBaseExists(@PathVariable Long id, Authentication authentication) {
        return accessContextResolver.resolve(authentication)
                .flatMap(access -> knowledgeBaseService
                        .getAuthorizedKnowledgeBase(id, access.userId(), access.isAdmin(), false)
                        .thenReturn(Boolean.TRUE));
    }
}
