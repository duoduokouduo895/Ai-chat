package org.example.ai_agent.filter;

import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
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

    private MockServerWebExchange createExchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/conversations/recent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer guest-token")
                        .build()
        );
    }
}
