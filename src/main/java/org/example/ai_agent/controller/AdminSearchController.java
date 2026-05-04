package org.example.ai_agent.controller;

import org.example.ai_agent.dto.admin.AdminSearchHistoryItemDto;
import org.example.ai_agent.entity.SearchHistory;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.repository.search.SearchHistoryRepository;
import org.example.ai_agent.service.SearchService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/search")
@CrossOrigin(origins = {"https://dduo.chat", "http://dduo.chat", "http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
public class AdminSearchController {

    private final SearchService searchService;
    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    public AdminSearchController(SearchService searchService,
                                 SearchHistoryRepository searchHistoryRepository,
                                 UserRepository userRepository) {
        this.searchService = searchService;
        this.searchHistoryRepository = searchHistoryRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/overview")
    public Mono<Map<String, Object>> getOverview() {
        Mono<String> indexStatusMono = searchService.checkIndexStatus();
        Mono<Long> totalHistoryMono = searchHistoryRepository.count();
        Mono<Long> todayHistoryMono = searchHistoryRepository.countByTimestampAfter(LocalDateTime.now().toLocalDate().atStartOfDay())
                .defaultIfEmpty(0L);

        return Mono.zip(indexStatusMono, totalHistoryMono, todayHistoryMono)
                .map(tuple -> {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("indexStatus", tuple.getT1());
                    payload.put("totalHistoryCount", tuple.getT2());
                    payload.put("todayHistoryCount", tuple.getT3());
                    return payload;
                });
    }

    @GetMapping("/history")
    public Mono<Map<String, Object>> getHistory(@RequestParam(defaultValue = "0") Integer page,
                                                @RequestParam(defaultValue = "10") Integer size,
                                                @RequestParam(required = false) String keyword) {
        int finalPage = page != null && page >= 0 ? page : 0;
        int finalSize = size != null && size > 0 && size <= 100 ? size : 10;
        long offset = (long) finalPage * finalSize;

        Mono<List<AdminSearchHistoryItemDto>> itemsMono = searchHistoryRepository.findAdminHistory(keyword, finalSize, offset)
                .flatMap(this::toDto)
                .collectList();
        Mono<Long> totalMono = searchHistoryRepository.countAdminHistory(keyword);

        return Mono.zip(itemsMono, totalMono)
                .map(tuple -> {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("items", tuple.getT1());
                    payload.put("total", tuple.getT2());
                    return payload;
                });
    }

    @PostMapping("/rebuild-indexes")
    public Mono<Map<String, Object>> rebuildIndexes() {
        return searchService.rebuildAllIndexes()
                .thenReturn(buildMessagePayload("索引重建任务已完成"));
    }

    @PostMapping("/index-all-conversations")
    public Mono<Map<String, Object>> indexAllConversations() {
        return searchService.indexAllExistingConversations()
                .map(this::buildMessagePayload);
    }

    private Mono<AdminSearchHistoryItemDto> toDto(SearchHistory history) {
        return userRepository.findById(history.getUserId())
                .map(user -> {
                    AdminSearchHistoryItemDto dto = new AdminSearchHistoryItemDto();
                    dto.setId(history.getId());
                    dto.setUserId(history.getUserId());
                    dto.setUsername(user.getUsername());
                    dto.setQuery(history.getQuery());
                    dto.setType(history.getType());
                    dto.setTimestamp(history.getTimestamp());
                    return dto;
                })
                .defaultIfEmpty(buildFallbackDto(history));
    }

    private AdminSearchHistoryItemDto buildFallbackDto(SearchHistory history) {
        AdminSearchHistoryItemDto dto = new AdminSearchHistoryItemDto();
        dto.setId(history.getId());
        dto.setUserId(history.getUserId());
        dto.setUsername("未知用户");
        dto.setQuery(history.getQuery());
        dto.setType(history.getType());
        dto.setTimestamp(history.getTimestamp());
        return dto;
    }

    private Map<String, Object> buildMessagePayload(String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        return payload;
    }
}


