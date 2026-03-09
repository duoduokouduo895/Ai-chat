package org.example.ai_agent.controller;

import org.example.ai_agent.document.ConversationDocument;
import org.example.ai_agent.dto.search.SearchHistoryDto;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.SearchService;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 搜索控制器
 * 提供对话内容的全文搜索API
 */
@RestController
@RequestMapping("/api/search")
@CrossOrigin(origins = {"http://localhost:3000", "http://dduo.chat", "https://dduo.chat"}, allowCredentials = "true")

public class SearchController {

    private final SearchService searchService;
    private final UserRepository userRepository; // 新增

    public SearchController(SearchService searchService, UserRepository userRepository) { // 新增
        this.searchService = searchService;
        this.userRepository = userRepository; // 新增
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
            @RequestParam(name = "query") String query,
            @RequestParam(name = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime startDate,
            @RequestParam(name = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime endDate,
            @RequestParam(name = "models", required = false) java.util.List<String> models,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .flatMap(userRepository::findByUsername)
                .flatMapMany(user -> searchService.searchConversations(user.getUserId(), query, startDate, endDate, models, page, size));
    }

    /**
     * 重建所有对话的索引
     *
     * @return 操作完成的信号
     */
    @PostMapping("/rebuild-indexes")
    public Mono<Void> rebuildIndexes() {
        return searchService.rebuildAllIndexes();
    }

    /**
     * 检查Elasticsearch索引状态
     *
     * @return 索引状态信息
     */
    @GetMapping("/index-status")
    public Mono<String> checkIndexStatus() {
        System.out.println("[DEBUG] 检查索引状态请求");
        return searchService.checkIndexStatus()
                .doOnNext(status -> System.out.println("[DEBUG] 索引状态: " + status))
                .doOnError(error -> {
                    System.out.println("[DEBUG] 检查索引状态出错: " + error.getMessage());
                    error.printStackTrace();
                });
    }

    /**
     * 手动索引所有现有对话
     *
     * @return 操作完成的信号
     */
    @PostMapping("/index-all-conversations")
    public Mono<String> indexAllConversations() {
        System.out.println("[DEBUG] 开始手动索引所有对话");
        return searchService.indexAllExistingConversations()
                .doOnSuccess(result -> System.out.println("[DEBUG] 手动索引完成: " + result))
                .doOnError(error -> {
                    System.out.println("[DEBUG] 手动索引出错: " + error.getMessage());
                    error.printStackTrace();
                });
    }

    /**
     * 获取搜索建议
     *
     * @param prefix 前缀
     * @param size   建议数量
     * @return 搜索建议
     */
    @GetMapping("/suggestions")
    public Mono<java.util.List<String>> getSearchSuggestions(@RequestParam String prefix, @RequestParam(defaultValue = "5") int size) {
        return searchService.getSearchSuggestions(prefix, size);
    }



    @PostMapping("/history")
    public Mono<Void> recordSearchHistory(@RequestBody SearchHistoryDto searchHistoryDto) {
        if (searchHistoryDto == null || searchHistoryDto.getQuery() == null || searchHistoryDto.getQuery().isEmpty()) {
            return Mono.empty(); 
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .flatMap(userRepository::findByUsername)
                .flatMap(user -> searchService.recordSearchHistory(user.getUserId(), searchHistoryDto.getQuery(), searchHistoryDto.getType()));
    }

    @GetMapping("/history")
    public Flux<ConversationDocument> getSearchHistory(@RequestParam(defaultValue = "20") int size) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .flatMap(userRepository::findByUsername)
                .flatMapMany(user -> searchService.getSearchHistory(user.getUserId(), size));
    }

    @DeleteMapping("/history")
    public Mono<Void> clearSearchHistory() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .flatMap(userRepository::findByUsername)
                .flatMap(user -> searchService.clearSearchHistory(user.getUserId()));
    }

    @PostMapping("/history/cleanup")
    public Mono<String> cleanupDuplicateSearchHistory() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .flatMap(userRepository::findByUsername)
                .flatMap(user -> searchService.cleanupDuplicateSearchHistory(user.getUserId()));
    }
}
