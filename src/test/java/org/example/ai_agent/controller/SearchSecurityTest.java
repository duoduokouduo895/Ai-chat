package org.example.ai_agent.controller;

import org.example.ai_agent.config.security.SecurityConfig;
import org.example.ai_agent.controller.support.RequestActorResolver;
import org.example.ai_agent.document.ConversationDocument;
import org.example.ai_agent.repository.auth.AdminUserRepository;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.repository.search.SearchHistoryRepository;
import org.example.ai_agent.service.SearchService;
import org.example.ai_agent.service.TicketService;
import org.example.ai_agent.util.JwtUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = {SearchController.class, AdminSearchController.class, SearchSecurityTest.TestOnlyController.class})
@Import({SecurityConfig.class, RequestActorResolver.class})
@TestPropertySource(properties = {
        "app.security.cors.allowed-origins=http://localhost:3000",
        "spring.cloud.nacos.config.import-check.enabled=false"
})
@Tag("high-risk")
class SearchSecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SearchService searchService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private SearchHistoryRepository searchHistoryRepository;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private AdminUserRepository adminUserRepository;
    @MockitoBean
    private TicketService ticketService;
    @MockitoBean
    private ReactiveStringRedisTemplate redisTemplate;

    @Test
    void searchEndpoint_shouldRejectUnauthenticatedRequests() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/search")
                        .queryParam("query", "合同")
                        .build())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testNamespace_shouldNotBeAnonymousInProductionSecurity() {
        webTestClient.get()
                .uri("/api/test/runtime")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void searchEndpoint_shouldAllowAuthenticatedUserRequests() {
        ConversationDocument document = new ConversationDocument(
                100L,
                42L,
                "合同检索",
                LocalDateTime.of(2026, 3, 28, 15, 30),
                "合同内容",
                List.of("user", "ai"),
                List.of("test-model"),
                2,
                LocalDateTime.of(2026, 3, 28, 15, 31)
        );

        mockUserJwt("user-token", "alice", 42L);
        when(searchService.searchConversations(42L, "合同", null, null, null, 0, 10))
                .thenReturn(Flux.just(document));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/search")
                        .queryParam("query", "合同")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].conversationId").isEqualTo(100)
                .jsonPath("$[0].title").isEqualTo("合同检索");

        verifyNoInteractions(userRepository);
    }

    @Test
    void suggestionsEndpoint_shouldUseAuthenticatedUserScope() {
        mockUserJwt("user-token", "alice", 42L);
        when(searchService.getSearchSuggestions(42L, "合同", 5))
                .thenReturn(Mono.just(List.of("合同", "合同模板")));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/search/suggestions")
                        .queryParam("prefix", "合同")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0]").isEqualTo("合同")
                .jsonPath("$[1]").isEqualTo("合同模板");

        verify(searchService).getSearchSuggestions(42L, "合同", 5);
    }

    @Test
    void searchEndpoint_shouldRejectOversizedPageBeforeCallingElasticsearch() {
        mockUserJwt("user-token", "alice", 42L);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/search")
                        .queryParam("query", "合同")
                        .queryParam("size", "101")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("VALIDATION_ERROR");

        verify(searchService, never()).searchConversations(any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void searchEndpoint_shouldRejectReversedDateRange() {
        mockUserJwt("user-token", "alice", 42L);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/search")
                        .queryParam("query", "合同")
                        .queryParam("startDate", "2026-07-15T00:00:00")
                        .queryParam("endDate", "2026-07-14T00:00:00")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BAD_REQUEST");

        verify(searchService, never()).searchConversations(any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void searchHistoryEndpoint_shouldRejectQueryLargerThanDatabaseColumn() {
        mockUserJwt("user-token", "alice", 42L);

        webTestClient.post()
                .uri("/api/search/history")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .bodyValue(java.util.Map.of("query", "x".repeat(256), "type", "basic"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("VALIDATION_ERROR");

        verify(searchService, never()).recordSearchHistory(any(), any(), any());
    }

    @Test
    void adminRebuildEndpoint_shouldRejectRegularUserRequests() {
        mockUserJwt("user-token", "alice", 42L);

        webTestClient.post()
                .uri("/api/admin/search/rebuild-indexes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void adminRebuildEndpoint_shouldAllowAdminRequests() {
        mockAdminJwt("admin-token", "root", 7L);
        when(searchService.rebuildAllIndexes()).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/api/admin/search/rebuild-indexes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("索引重建任务已完成");
    }

    private void mockUserJwt(String token, String username, Long userId) {
        when(jwtUtil.parseAuthenticationClaims(token)).thenReturn(
                new JwtUtil.AuthenticationClaims(username, "jti-" + userId, "USER", userId, null, false));
        when(redisTemplate.hasKey("token:jti:jti-" + userId)).thenReturn(Mono.just(true));
        when(redisTemplate.hasKey("token:user-revoked:" + userId)).thenReturn(Mono.just(false));
    }

    private void mockAdminJwt(String token, String username, Long adminId) {
        when(jwtUtil.parseAuthenticationClaims(token)).thenReturn(
                new JwtUtil.AuthenticationClaims(username, "admin-jti-" + adminId, "ADMIN", null, adminId, false));
        when(redisTemplate.hasKey("token:jti:admin-jti-" + adminId)).thenReturn(Mono.just(true));
    }

    @RestController
    static class TestOnlyController {

        @GetMapping("/api/test/runtime")
        Mono<String> runtime() {
            return Mono.just("test-only");
        }
    }
}
