package org.example.ai_agent.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.ai_agent.config.security.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilAuthenticationTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        config.setSecret(Base64.getUrlEncoder().withoutPadding()
                .encodeToString("jwt-test-secret-with-at-least-256-bits".getBytes(StandardCharsets.UTF_8)));
        config.setExpiration(60_000L);
        config.setRefreshWindow(300_000L);
        jwtUtil = new JwtUtil(config);
    }

    @Test
    void parseAuthenticationClaims_shouldReturnTypedUserClaims() {
        String token = jwtUtil.generateToken("alice", 42L, true);

        JwtUtil.AuthenticationClaims claims = jwtUtil.parseAuthenticationClaims(token);

        assertEquals("alice", claims.username());
        assertEquals("USER", claims.principalType());
        assertEquals(42L, claims.userId());
        assertTrue(claims.guest());
        assertTrue(jwtUtil.isTokenValidForAuthentication(token));
    }

    @Test
    void parseAuthenticationClaims_shouldRejectMissingClaimAndInvalidSignature() {
        SecretKey key = Keys.hmacShaKeyFor("jwt-test-secret-with-at-least-256-bits".getBytes(StandardCharsets.UTF_8));
        String missingPrincipalType = Jwts.builder()
                .id("jti-1")
                .subject("alice")
                .claim("userId", 42L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
        String invalidSignature = Jwts.builder()
                .id("jti-2")
                .subject("alice")
                .claim("principalType", "USER")
                .claim("userId", 42L)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(new byte[32]))
                .compact();

        assertThrows(JwtUtil.InvalidAuthenticationTokenException.class,
                () -> jwtUtil.parseAuthenticationClaims(missingPrincipalType));
        assertThrows(JwtUtil.InvalidAuthenticationTokenException.class,
                () -> jwtUtil.parseAuthenticationClaims(invalidSignature));
        assertFalse(jwtUtil.isTokenValidForAuthentication(invalidSignature));
    }

    @Test
    void parseAuthenticationClaims_shouldRejectTokenWithoutExpiration() {
        SecretKey key = Keys.hmacShaKeyFor("jwt-test-secret-with-at-least-256-bits".getBytes(StandardCharsets.UTF_8));
        String tokenWithoutExpiration = Jwts.builder()
                .id("jti-without-exp")
                .subject("alice")
                .claim("principalType", "USER")
                .claim("userId", 42L)
                .issuedAt(new Date())
                .signWith(key)
                .compact();

        assertThrows(JwtUtil.InvalidAuthenticationTokenException.class,
                () -> jwtUtil.parseAuthenticationClaims(tokenWithoutExpiration));
        assertFalse(jwtUtil.isTokenValidForAuthentication(tokenWithoutExpiration));
    }

    @Test
    void parseAuthenticationClaims_shouldRejectExpiredTokenWhileLegacyGetterKeepsRefreshClaims() {
        SecretKey key = Keys.hmacShaKeyFor("jwt-test-secret-with-at-least-256-bits".getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .id("jti-expired")
                .subject("alice")
                .claim("principalType", "USER")
                .claim("userId", 42L)
                .claim("isGuest", false)
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key)
                .compact();

        assertThrows(JwtUtil.InvalidAuthenticationTokenException.class,
                () -> jwtUtil.parseAuthenticationClaims(expiredToken));
        assertFalse(jwtUtil.isTokenValidForAuthentication(expiredToken));
        assertEquals(42L, jwtUtil.getUserIdFromToken(expiredToken));
    }
}
