package org.example.ai_agent.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.ai_agent.document.ConversationDocument;
import org.example.ai_agent.controller.support.RequestActorResolver;
import org.example.ai_agent.dto.search.SearchHistoryDto;
import org.example.ai_agent.service.SearchService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 搜索控制器
 * 提供对话内容的全文搜索API
 */
@Validated
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    private final RequestActorResolver requestActorResolver;

    public SearchController(SearchService searchService, RequestActorResolver requestActorResolver) {
        this.searchService = searchService;
        this.requestActorResolver = requestActorResolver;
    }

    /**
     * 统一搜索接口，支持全文搜索和高级筛选
     *
     * @param query     搜索关键词
     * @param startDate 开始时间 (ISO 8601 格式, e.g., "2023-01-01T00:00:00")
     * @param endDate   结束时间 (ISO 8601 格式)
     * @param models    AI模型列表
     * @param page      页码
     * @param size      每页大小
     * @return 搜索结果
     */
    @GetMapping
    public Flux<ConversationDocument> searchConversations(
            @RequestParam(name = "query") @NotBlank @Size(max = 255) String query,
            @RequestParam(name = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime startDate,
            @RequestParam(name = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime endDate,
            @RequestParam(name = "models", required = false) @Size(max = 20) java.util.List<@NotBlank @Size(max = 120) String> models,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(name = "size", defaultValue = "10") @Min(1) @Max(100) int size) {

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return Flux.error(new IllegalArgumentException("开始时间不能晚于结束时间"));
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(requestActorResolver::requireLegacyUserScopedActorId)
                .flatMapMany(userId -> searchService.searchConversations(userId, query, startDate, endDate, models, page, size));
    }

    /**
     * 获取搜索建议
     *
     * @param prefix 前缀
     * @param size   建议数量
    * @return 搜索建议
     */
    @GetMapping("/suggestions")
    public Mono<java.util.List<String>> getSearchSuggestions(
            @RequestParam @NotBlank @Size(max = 100) String prefix,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int size) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(requestActorResolver::requireLegacyUserScopedActorId)
                .flatMap(userId -> searchService.getSearchSuggestions(userId, prefix, size));
    }



    @PostMapping("/history")
    public Mono<Void> recordSearchHistory(@Valid @RequestBody SearchHistoryDto searchHistoryDto) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(requestActorResolver::requireLegacyUserScopedActorId)
                .flatMap(userId -> searchService.recordSearchHistory(userId, searchHistoryDto.getQuery(), searchHistoryDto.getType()));
    }

    @GetMapping("/history")
    public Flux<ConversationDocument> getSearchHistory(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(requestActorResolver::requireLegacyUserScopedActorId)
                .flatMapMany(userId -> searchService.getSearchHistory(userId, size));
    }

    @DeleteMapping("/history")
    public Mono<Void> clearSearchHistory() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(requestActorResolver::requireLegacyUserScopedActorId)
                .flatMap(searchService::clearSearchHistory);
    }

    @PostMapping("/history/cleanup")
    public Mono<String> cleanupDuplicateSearchHistory() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(requestActorResolver::requireLegacyUserScopedActorId)
                .flatMap(searchService::cleanupDuplicateSearchHistory);
    }

}


