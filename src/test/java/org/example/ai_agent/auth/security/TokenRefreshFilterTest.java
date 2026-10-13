package org.example.ai_agent.auth.security;

import org.example.ai_agent.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRefreshFilterTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    @Mock
    private ReactiveSetOperations<String, String> setOperations;
    @Mock
    private UserRepository userRepository;

    @Test
    void guestActivityTouchesDatabaseOnlyOnceWithinWriteInterval() {
        when(jwtUtil.getIsGuestFromToken("guest-token")).thenReturn(true);
        when(jwtUtil.getUserIdFromToken("guest-token")).thenReturn(42L);
        when(jwtUtil.parseRefreshClaims("guest-token"))
                .thenThrow(new JwtUtil.InvalidAuthenticationTokenException("not in refresh window"));
        when(userRepository.touchGuestLastActiveAt(any(), any(), any())).thenReturn(Mono.just(1));

        TokenRefreshFilter filter = new TokenRefreshFilter(jwtUtil, redisTemplate, userRepository);
        WebFilterChain chain = ignored -> ignored.getResponse().setComplete();

        StepVerifier.create(filter.filter(createExchange(), chain))
                .verifyComplete();
        StepVerifier.create(filter.filter(createExchange(), chain))
                .verifyComplete();

        verify(userRepository, times(1)).touchGuestLastActiveAt(any(), any(), any());
    }

    @Test
    void tokenRefreshRedisFailureShouldNotBreakSuccessfulBusinessResponse() {
        when(jwtUtil.getIsGuestFromToken("user-token")).thenReturn(false);
        when(jwtUtil.parseRefreshClaims("user-token")).thenReturn(
                new JwtUtil.AuthenticationClaims("alice", "old-jti", "USER", 42L, null, false));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(TokenRedisKeys.refreshJti("old-jti")))
                .thenReturn(Mono.error(new RuntimeException("redis unavailable")));

        TokenRefreshFilter filter = new TokenRefreshFilter(jwtUtil, redisTemplate, userRepository);
        WebFilterChain chain = ignored -> ignored.getResponse().setComplete();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/conversations/recent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                        .build()
        );

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        org.junit.jupiter.api.Assertions.assertFalse(
                exchange.getResponse().getHeaders().containsHeader("X-Refreshed-Token"));
        verify(jwtUtil, never()).refreshToken("user-token");
    }

    @Test
    void revokedOrConsumedRefreshSessionDoesNotIssueANewToken() {
        when(jwtUtil.getIsGuestFromToken("user-token")).thenReturn(false);
        when(jwtUtil.parseRefreshClaims("user-token")).thenReturn(
                new JwtUtil.AuthenticationClaims("alice", "old-jti", "USER", 42L, null, false));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(TokenRedisKeys.refreshJti("old-jti"))).thenReturn(Mono.empty());

        TokenRefreshFilter filter = new TokenRefreshFilter(jwtUtil, redisTemplate, userRepository);
        WebFilterChain chain = ignored -> ignored.getResponse().setComplete();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/conversations/recent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(jwtUtil, never()).refreshToken("user-token");
        org.junit.jupiter.api.Assertions.assertFalse(
                exchange.getResponse().getHeaders().containsHeader("X-Refreshed-Token"));
    }

    @Test
    void refreshSessionAtomicallyRotatesTheRefreshJti() {
        Date expiration = new Date(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
        when(jwtUtil.getIsGuestFromToken("user-token")).thenReturn(false);
        when(jwtUtil.parseRefreshClaims("user-token")).thenReturn(
                new JwtUtil.AuthenticationClaims("alice", "old-jti", "USER", 42L, null, false));
        when(jwtUtil.refreshToken("user-token")).thenReturn("refreshed-token");
        when(jwtUtil.getIdFromToken("refreshed-token")).thenReturn("new-jti");
        when(jwtUtil.getExpirationDateFromToken("refreshed-token")).thenReturn(expiration);
        when(jwtUtil.refreshSessionTtl("refreshed-token")).thenReturn(Duration.ofHours(1));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.getAndDelete(TokenRedisKeys.refreshJti("old-jti"))).thenReturn(Mono.just("alice"));
        when(valueOperations.setIfAbsent(eq(TokenRedisKeys.jti("new-jti")), eq("alice"), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(valueOperations.set(eq(TokenRedisKeys.refreshJti("new-jti")), eq("alice"), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(setOperations.add(TokenRedisKeys.userJtis(42L), "new-jti")).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(eq(TokenRedisKeys.userJtis(42L)), any(Duration.class))).thenReturn(Mono.just(true));
        when(redisTemplate.delete(TokenRedisKeys.jti("old-jti"))).thenReturn(Mono.just(1L));
        when(setOperations.remove(TokenRedisKeys.userJtis(42L), "old-jti")).thenReturn(Mono.just(1L));

        TokenRefreshFilter filter = new TokenRefreshFilter(jwtUtil, redisTemplate, userRepository);
        WebFilterChain chain = ignored -> ignored.getResponse().setComplete();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/conversations/recent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        org.junit.jupiter.api.Assertions.assertEquals("refreshed-token",
                exchange.getResponse().getHeaders().getFirst("X-Refreshed-Token"));
        verify(redisTemplate).delete(TokenRedisKeys.jti("old-jti"));
        verify(setOperations).remove(TokenRedisKeys.userJtis(42L), "old-jti");
        verify(valueOperations).set(eq(TokenRedisKeys.refreshJti("new-jti")), eq("alice"), any(Duration.class));
    }

    private MockServerWebExchange createExchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/conversations/recent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer guest-token")
                        .build()
        );
    }
}
