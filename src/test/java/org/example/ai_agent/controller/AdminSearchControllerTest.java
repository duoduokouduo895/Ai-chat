package org.example.ai_agent.controller;

import org.example.ai_agent.entity.SearchHistory;
import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.repository.search.SearchHistoryRepository;
import org.example.ai_agent.service.SearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSearchControllerTest {

    @Mock
    private SearchService searchService;

    @Mock
    private SearchHistoryRepository searchHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void historyUsesBatchUserLookup() {
        SearchHistory history = history(7L, 42L);
        User user = new User();
        user.setUserId(42L);
        user.setUsername("tester");

        when(searchHistoryRepository.findAdminHistory(null, 10, 0L)).thenReturn(Flux.just(history));
        when(searchHistoryRepository.countAdminHistory(null)).thenReturn(Mono.just(1L));
        when(userRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any()))
                .thenReturn(Flux.just(user));

        StepVerifier.create(controller().getHistory(0, 10, null, null, null, true))
                .assertNext(payload -> {
                    org.assertj.core.api.Assertions.assertThat(payload.get("total")).isEqualTo(1L);
                    org.assertj.core.api.Assertions.assertThat(payload.get("nextId")).isEqualTo(7L);
                })
                .verifyComplete();

        verify(userRepository).findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any());
        verify(userRepository, never()).findById(any(Long.class));
    }

    @Test
    void cursorHistoryCanSkipTotalCount() {
        LocalDateTime cursorTimestamp = LocalDateTime.of(2026, 5, 30, 20, 0);
        when(searchHistoryRepository.findHistoryKeyset(null, cursorTimestamp, 8L, 10))
                .thenReturn(Flux.empty());

        StepVerifier.create(controller().getHistory(0, 10, null, cursorTimestamp, 8L, false))
                .assertNext(payload -> org.assertj.core.api.Assertions.assertThat(payload)
                        .doesNotContainKey("total"))
                .verifyComplete();

        verify(searchHistoryRepository, never()).countAdminHistory(null);
    }

    @Test
    void cursorRequiresTimestampAndIdTogether() {
        StepVerifier.create(controller().getHistory(0, 10, null, LocalDateTime.now(), null, false))
                .expectError(ResponseStatusException.class)
                .verify();
    }

    private AdminSearchController controller() {
        return new AdminSearchController(searchService, searchHistoryRepository, userRepository);
    }

    private SearchHistory history(Long id, Long userId) {
        SearchHistory history = new SearchHistory();
        history.setId(id);
        history.setUserId(userId);
        history.setQuery("测试");
        history.setTimestamp(LocalDateTime.of(2026, 5, 30, 19, 0));
        return history;
    }
}
