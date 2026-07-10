package org.example.ai_agent.config.security;

import org.example.ai_agent.filter.TokenRefreshFilter;
import org.example.ai_agent.repository.auth.AdminUserRepository;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.TicketService;
import org.example.ai_agent.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private TokenRefreshFilter tokenRefreshFilter;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Test
    void jwtAuthenticationUsesSignedUserIdWithoutLoadingUserRecord() {
        SecurityConfig securityConfig = new SecurityConfig(
                jwtUtil,
                tokenRefreshFilter,
                userRepository,
                adminUserRepository,
                ticketService,
                redisTemplate,
                "http://localhost:3000"
        );
        when(jwtUtil.getUsernameFromToken("user-token")).thenReturn("alice");
        when(jwtUtil.getIdFromToken("user-token")).thenReturn("jti-42");
        when(jwtUtil.getPrincipalTypeFromToken("user-token")).thenReturn("USER");
        when(jwtUtil.getUserIdFromToken("user-token")).thenReturn(42L);
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
                tokenRefreshFilter,
                userRepository,
                adminUserRepository,
                ticketService,
                redisTemplate,
                "http://localhost:3000"
        );
        when(jwtUtil.getUsernameFromToken("user-token")).thenReturn("alice");
        when(jwtUtil.getIdFromToken("user-token")).thenReturn("jti-42");
        when(jwtUtil.getPrincipalTypeFromToken("user-token")).thenReturn("USER");
        when(jwtUtil.getUserIdFromToken("user-token")).thenReturn(42L);
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
                tokenRefreshFilter,
                userRepository,
                adminUserRepository,
                ticketService,
                redisTemplate,
                "http://localhost:3000"
        );
        when(jwtUtil.getUsernameFromToken("guest-token")).thenReturn("guest");
        when(jwtUtil.getIdFromToken("guest-token")).thenReturn("guest-jti");
        when(jwtUtil.getPrincipalTypeFromToken("guest-token")).thenReturn("USER");
        when(jwtUtil.getUserIdFromToken("guest-token")).thenReturn(99L);
        when(jwtUtil.getIsGuestFromToken("guest-token")).thenReturn(true);
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
}
