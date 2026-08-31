package org.example.ai_agent.controller;

import org.example.ai_agent.service.SearchService;
import org.example.ai_agent.service.admin.AdminSearchQuery;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/search")
public class AdminSearchController {

    private final SearchService searchService;
    private final AdminSearchQuery adminSearchQuery;

    public AdminSearchController(SearchService searchService,
                                 AdminSearchQuery adminSearchQuery) {
        this.searchService = searchService;
        this.adminSearchQuery = adminSearchQuery;
    }

    @GetMapping("/overview")
    public Mono<Map<String, Object>> getOverview() {
        return adminSearchQuery.getOverview();
    }

    @GetMapping("/history")
    public Mono<Map<String, Object>> getHistory(@RequestParam(defaultValue = "0") Integer page,
                                                @RequestParam(defaultValue = "10") Integer size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) LocalDateTime lastTimestamp,
                                                @RequestParam(required = false) Long lastId,
                                                @RequestParam(defaultValue = "true") Boolean includeTotal) {
        int finalPage = page != null && page >= 0 ? page : 0;
        int finalSize = size != null && size > 0 && size <= 100 ? size : 10;
        boolean useCursor = lastTimestamp != null || lastId != null;

        if ((lastTimestamp == null) != (lastId == null)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "lastTimestamp 与 lastId 必须同时传入"
            ));
        }

        return adminSearchQuery.getHistory(finalPage, finalSize, keyword, lastTimestamp, lastId,
                !Boolean.FALSE.equals(includeTotal));
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

    private Map<String, Object> buildMessagePayload(String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("message", message);
        return payload;
    }
}


