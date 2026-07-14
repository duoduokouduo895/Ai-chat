package org.example.ai_agent.filter;

import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
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
    private UserRepository userRepository;

    @Test
    void guestActivityTouchesDatabaseOnlyOnceWithinWriteInterval() {
        when(jwtUtil.getIsGuestFromToken("guest-token")).thenReturn(true);
        when(jwtUtil.getUserIdFromToken("guest-token")).thenReturn(42L);
        when(jwtUtil.shouldRefreshToken("guest-token")).thenReturn(false);
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
        when(jwtUtil.shouldRefreshToken("user-token")).thenReturn(true);
        when(jwtUtil.getUsernameFromToken("user-token")).thenReturn("alice");
        when(jwtUtil.getUserIdFromToken("user-token")).thenReturn(42L);
        when(jwtUtil.refreshToken("user-token")).thenReturn("refreshed-token");
        when(jwtUtil.getIdFromToken("refreshed-token")).thenReturn("new-jti");
        when(jwtUtil.getExpirationDateFromToken("refreshed-token"))
                .thenReturn(new Date(System.currentTimeMillis() + Duration.ofHours(1).toMillis()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("token:jti:new-jti"), eq("alice"), any(Duration.class)))
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
    }

    private MockServerWebExchange createExchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/conversations/recent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer guest-token")
                        .build()
        );
    }
}
