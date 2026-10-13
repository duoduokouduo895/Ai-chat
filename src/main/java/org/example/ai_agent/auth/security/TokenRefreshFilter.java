package org.example.ai_agent.auth.security;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.auth.repository.UserRepository;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class TokenRefreshFilter implements WebFilter {

    private static final Duration GUEST_ACTIVITY_WRITE_INTERVAL = Duration.ofMinutes(5);
    private static final int GUEST_ACTIVITY_CACHE_PRUNE_THRESHOLD = 10_000;

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final ConcurrentMap<Long, Long> guestActivityWriteTimes = new ConcurrentHashMap<>();

    public TokenRefreshFilter(JwtUtil jwtUtil,
                              ReactiveStringRedisTemplate redisTemplate,
                              UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> checkAndRefreshToken(exchange));
        return chain.filter(exchange);
    }

    private Mono<Void> checkAndRefreshToken(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String requestPath = request.getPath().value();

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String authToken = authHeader.substring(7).trim();
            try {
                return refreshTokenIfNeeded(exchange, requestPath, authToken)
                        .then(touchGuestActivity(authToken));
            } catch (Exception e) {
                logTokenRefreshException(requestPath, e);
            }
        } else if (!isPublicPath(requestPath)) {
            log.debug("Token 检查跳过: 请求缺少 Authorization Header, path={}", requestPath);
        }

        return Mono.empty();
    }

    private void logTokenRefreshException(String requestPath, Exception e) {
        log.warn("Token 刷新异常: path={}, errorType={}", requestPath, e.getClass().getSimpleName());
    }

    private Mono<Void> touchGuestActivity(String authToken) {
        if (!Boolean.TRUE.equals(jwtUtil.getIsGuestFromToken(authToken))) {
            return Mono.empty();
        }

        Long userId = jwtUtil.getUserIdFromToken(authToken);
        if (userId == null) {
            return Mono.empty();
        }

        long nowMillis = System.currentTimeMillis();
        if (!shouldWriteGuestActivity(userId, nowMillis)) {
            return Mono.empty();
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime writeThreshold = now.minus(GUEST_ACTIVITY_WRITE_INTERVAL);
        return userRepository.touchGuestLastActiveAt(userId, now, writeThreshold)
                .onErrorResume(error -> {
                    log.warn("游客活跃时间更新失败: errorType={}", error.getClass().getSimpleName());
                    return Mono.empty();
                })
                .then();
    }

    private boolean shouldWriteGuestActivity(Long userId, long nowMillis) {
        AtomicBoolean shouldWrite = new AtomicBoolean(false);
        long intervalMillis = GUEST_ACTIVITY_WRITE_INTERVAL.toMillis();
        guestActivityWriteTimes.compute(userId, (ignored, previousWriteMillis) -> {
            if (previousWriteMillis == null || nowMillis - previousWriteMillis >= intervalMillis) {
                shouldWrite.set(true);
                return nowMillis;
            }
            return previousWriteMillis;
        });

        if (guestActivityWriteTimes.size() > GUEST_ACTIVITY_CACHE_PRUNE_THRESHOLD) {
            guestActivityWriteTimes.entrySet()
                    .removeIf(entry -> nowMillis - entry.getValue() >= intervalMillis * 2);
        }
        return shouldWrite.get();
    }

    private Mono<Void> refreshTokenIfNeeded(ServerWebExchange exchange, String requestPath, String authToken) {
        final JwtUtil.AuthenticationClaims claims;
        try {
            claims = jwtUtil.parseRefreshClaims(authToken);
        } catch (JwtUtil.InvalidAuthenticationTokenException error) {
            return Mono.empty();
        }
        if (claims == null) {
            return Mono.empty();
        }

        return redisTemplate.opsForValue().getAndDelete(TokenRedisKeys.refreshJti(claims.jti()))
                .timeout(Duration.ofSeconds(2))
                .flatMap(ignored -> issueRefreshedToken(exchange, requestPath, authToken, claims))
                .doOnError(error -> log.warn("Token 刷新失败: 新 JTI 写入 Redis 异常, path={}, errorType={}",
                        requestPath, error.getClass().getSimpleName()))
                .onErrorResume(error -> Mono.empty())
                .then();
    }

    private Mono<Void> issueRefreshedToken(ServerWebExchange exchange,
                                           String requestPath,
                                           String authToken,
                                           JwtUtil.AuthenticationClaims claims) {
        String refreshedToken = jwtUtil.refreshToken(authToken);
        String newJti = jwtUtil.getIdFromToken(refreshedToken);
        Date expiration = jwtUtil.getExpirationDateFromToken(refreshedToken);
        if (newJti == null || expiration == null) {
            return Mono.error(new IllegalStateException("刷新后的Token缺少必要Claim"));
        }

        Duration accessTtl = Duration.ofMillis(expiration.getTime() - System.currentTimeMillis());
        if (accessTtl.isNegative() || accessTtl.isZero()) {
            return Mono.error(new IllegalStateException("刷新后的Token已过期"));
        }
        Duration refreshTtl = jwtUtil.refreshSessionTtl(refreshedToken);
        String subject = claims.principalType().equals("ADMIN")
                ? "admin:" + claims.username()
                : claims.username();

        return redisTemplate.opsForValue()
                .setIfAbsent(TokenRedisKeys.jti(newJti), subject, accessTtl)
                .flatMap(saved -> Boolean.TRUE.equals(saved)
                        ? redisTemplate.opsForValue().set(TokenRedisKeys.refreshJti(newJti), subject, refreshTtl)
                        : Mono.error(new IllegalStateException("新JTI注册冲突")))
                .then(registerNewJti(claims.userId(), newJti, refreshTtl))
                .then(redisTemplate.delete(TokenRedisKeys.jti(claims.jti())))
                .then(removeOldJti(claims.userId(), claims.jti()))
                .doOnSuccess(unused -> {
                    exchange.getResponse().getHeaders().set("X-Refreshed-Token", refreshedToken);
                    log.debug("Token 刷新成功: path={}", requestPath);
                });
    }

    private Mono<Void> registerNewJti(Long userId, String newJti, Duration refreshTtl) {
        return userId == null
                ? Mono.empty()
                : redisTemplate.opsForSet().add(TokenRedisKeys.userJtis(userId), newJti)
                        .then(redisTemplate.expire(TokenRedisKeys.userJtis(userId),
                                refreshTtl))
                        .then();
    }

    private Mono<Void> removeOldJti(Long userId, String oldJti) {
        return userId == null
                ? Mono.empty()
                : redisTemplate.opsForSet().remove(TokenRedisKeys.userJtis(userId), oldJti).then();
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/users/register")
                || path.startsWith("/api/users/login")
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info");
    }
}
