package org.example.ai_agent.admin.service;

import org.example.ai_agent.admin.dto.AdminSearchHistoryItemDto;
import org.example.ai_agent.search.entity.SearchHistory;
import org.example.ai_agent.auth.entity.User;
import org.example.ai_agent.auth.repository.UserRepository;
import org.example.ai_agent.search.repository.SearchHistoryRepository;
import org.example.ai_agent.search.service.SearchService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RepositoryAdminSearchQuery implements AdminSearchQuery {

    private final SearchService searchService;
    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    public RepositoryAdminSearchQuery(SearchService searchService,
                                      SearchHistoryRepository searchHistoryRepository,
                                      UserRepository userRepository) {
        this.searchService = searchService;
        this.searchHistoryRepository = searchHistoryRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Mono<Map<String, Object>> getOverview() {
        Mono<String> indexStatusMono = searchService.checkIndexStatus();
        Mono<Long> totalHistoryMono = searchHistoryRepository.count();
        Mono<Long> todayHistoryMono = searchHistoryRepository
                .countByTimestampAfter(LocalDateTime.now().toLocalDate().atStartOfDay())
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

    @Override
    public Mono<Map<String, Object>> getHistory(int page, int size, String keyword,
                                                LocalDateTime lastTimestamp, Long lastId, boolean includeTotal) {
        long offset = (long) page * size;
        boolean useCursor = lastTimestamp != null || lastId != null;
        Mono<List<AdminSearchHistoryItemDto>> itemsMono = (useCursor
                ? searchHistoryRepository.findHistoryKeyset(keyword, lastTimestamp, lastId, size)
                : searchHistoryRepository.findAdminHistory(keyword, size, offset))
                .collectList()
                .flatMap(this::toDtos);
        Mono<Long> totalMono = includeTotal
                ? searchHistoryRepository.countAdminHistory(keyword)
                : Mono.just(-1L);

        return Mono.zip(itemsMono, totalMono)
                .map(tuple -> {
                    Map<String, Object> payload = new HashMap<>();
                    List<AdminSearchHistoryItemDto> items = tuple.getT1();
                    payload.put("items", items);
                    if (tuple.getT2() >= 0) {
                        payload.put("total", tuple.getT2());
                    }
                    if (!items.isEmpty()) {
                        AdminSearchHistoryItemDto lastItem = items.get(items.size() - 1);
                        payload.put("nextTimestamp", lastItem.getTimestamp());
                        payload.put("nextId", lastItem.getId());
                    }
                    return payload;
                });
    }

    private Mono<List<AdminSearchHistoryItemDto>> toDtos(List<SearchHistory> histories) {
        if (histories.isEmpty()) {
            return Mono.just(List.of());
        }
        List<Long> userIds = histories.stream()
                .map(SearchHistory::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return userRepository.findAllById(userIds)
                .collectMap(User::getUserId, User::getUsername)
                .map(usernameByUserId -> histories.stream()
                        .map(history -> buildDto(history, usernameByUserId.get(history.getUserId())))
                        .collect(Collectors.toList()));
    }

    private AdminSearchHistoryItemDto buildDto(SearchHistory history, String username) {
        AdminSearchHistoryItemDto dto = new AdminSearchHistoryItemDto();
        dto.setId(history.getId());
        dto.setUserId(history.getUserId());
        dto.setUsername(username != null ? username : "未知用户");
        dto.setQuery(history.getQuery());
        dto.setType(history.getType());
        dto.setTimestamp(history.getTimestamp());
        return dto;
    }
}
