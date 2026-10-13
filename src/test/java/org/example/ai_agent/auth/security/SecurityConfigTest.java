package org.example.ai_agent.auth.security;

import org.example.ai_agent.auth.repository.AdminUserRepository;
import org.example.ai_agent.auth.repository.UserRepository;
import org.example.ai_agent.auth.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.cors.CorsConfiguration;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Test
    void productionCorsAllowsCanonicalFrontendAndRejectsUnknownOrigins() throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(
                Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        SecurityConfig securityConfig = new SecurityConfig(
                jwtUtil,
                userRepository,
                adminUserRepository,
                ticketService,
                redisTemplate,
                properties.getProperty("app.security.cors.allowed-origins")
        );
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .options("/api/ai/models")
                .header("Origin", "https://dduo.chat")
                .header("Access-Control-Request-Method", "GET")
                .build());

        CorsConfiguration configuration = securityConfig.corsConfigurationSource()
                .getCorsConfiguration(exchange);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getAllowedOrigins())
                .contains("https://dduo.chat", "https://app.dduo.chat", "https://app.dduo.me")
                .doesNotContain("*");
        assertThat(configuration.checkOrigin("https://dduo.chat")).isEqualTo("https://dduo.chat");
        assertThat(configuration.checkOrigin("https://app.dduo.me")).isEqualTo("https://app.dduo.me");
        assertThat(configuration.checkOrigin("https://attacker.example")).isNull();
    }

    @Test
    void jwtAuthenticationUsesSignedUserIdWithoutLoadingUserRecord() {
        SecurityConfig securityConfig = new SecurityConfig(
                jwtUtil,
                userRepository,
                adminUserRepository,
                ticketService,
                redisTemplate,
                "http://localhost:3000"
        );
        when(jwtUtil.parseAuthenticationClaims("user-token")).thenReturn(
                new JwtUtil.AuthenticationClaims("alice", "jti-42", "USER", 42L, null, false));
        when(redisTemplate.hasKey("token:jti:jti-42")).thenReturn(Mono.just(true));
        when(redisTemplate.hasKey("token:user-revoked:42")).thenReturn(Mono.just(false));

        StepVerifier.create(securityConfig.combinedAuthenticationManager()
                        .authenticate(new UsernamePasswordAuthenticationToken("jwt", "user-token")))
                .assertNext(authentication -> {
                    org.junit.jupiter.api.Assertions.assertEquals("alice", authentication.getName());
                    org.junit.jupiter.api.Assertions.assertEquals(42L, authentication.getDetails());
                })
                .verifyComplete();

        verifyNoInteractions(userRepository);
    }

    @Test
    void jwtAuthenticationRejectsDeletedUserMarker() {
        SecurityConfig securityConfig = new SecurityConfig(
                jwtUtil,
                userRepository,
                adminUserRepository,
                ticketService,
                redisTemplate,
                "http://localhost:3000"
        );
        when(jwtUtil.parseAuthenticationClaims("user-token")).thenReturn(
                new JwtUtil.AuthenticationClaims("alice", "jti-42", "USER", 42L, null, false));
        when(redisTemplate.hasKey("token:jti:jti-42")).thenReturn(Mono.just(true));
        when(redisTemplate.hasKey("token:user-revoked:42")).thenReturn(Mono.just(true));

        StepVerifier.create(securityConfig.combinedAuthenticationManager()
                        .authenticate(new UsernamePasswordAuthenticationToken("jwt", "user-token")))
                .expectError(org.springframework.security.authentication.BadCredentialsException.class)
                .verify();
    }

    @Test
    void guestJwtAuthenticationAddsGuestAuthorityWithoutChangingUserRole() {
        SecurityConfig securityConfig = new SecurityConfig(
                jwtUtil,
                userRepository,
                adminUserRepository,
                ticketService,
                redisTemplate,
                "http://localhost:3000"
        );
        when(jwtUtil.parseAuthenticationClaims("guest-token")).thenReturn(
                new JwtUtil.AuthenticationClaims("guest", "guest-jti", "USER", 99L, null, true));
        when(redisTemplate.hasKey("token:jti:guest-jti")).thenReturn(Mono.just(true));
        when(redisTemplate.hasKey("token:user-revoked:99")).thenReturn(Mono.just(false));

        StepVerifier.create(securityConfig.combinedAuthenticationManager()
                        .authenticate(new UsernamePasswordAuthenticationToken("jwt", "guest-token")))
                .assertNext(authentication -> {
                    org.junit.jupiter.api.Assertions.assertTrue(authentication.getAuthorities().stream()
                            .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority())));
                    org.junit.jupiter.api.Assertions.assertTrue(authentication.getAuthorities().stream()
                            .anyMatch(authority -> "ROLE_GUEST".equals(authority.getAuthority())));
                })
                .verifyComplete();
    }

    @Test
    void jwtAuthenticationRejectsExpiredTokenBeforeRedisLookup() {
        SecurityConfig securityConfig = new SecurityConfig(
                jwtUtil,
                userRepository,
                adminUserRepository,
                ticketService,
                redisTemplate,
                "http://localhost:3000"
        );
        when(jwtUtil.parseAuthenticationClaims("expired-token"))
                .thenThrow(new JwtUtil.InvalidAuthenticationTokenException("Token已过期"));

        StepVerifier.create(securityConfig.combinedAuthenticationManager()
                        .authenticate(new UsernamePasswordAuthenticationToken("jwt", "expired-token")))
                .expectError(org.springframework.security.authentication.BadCredentialsException.class)
                .verify();

        org.mockito.Mockito.verifyNoInteractions(redisTemplate);
    }
}
