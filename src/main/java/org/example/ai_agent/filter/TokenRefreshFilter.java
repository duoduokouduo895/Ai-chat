package org.example.ai_agent.filter;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.util.JwtUtil;
import org.example.ai_agent.util.TokenRedisKeys;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
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
@Component
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
                return touchGuestActivity(authToken)
                        .then(refreshTokenIfNeeded(exchange, requestPath, authToken));
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
        if (!jwtUtil.shouldRefreshToken(authToken)) {
            return Mono.empty();
        }

        String username = jwtUtil.getUsernameFromToken(authToken);
        Long userId = jwtUtil.getUserIdFromToken(authToken);

        String refreshedToken = jwtUtil.refreshToken(authToken);
        if (refreshedToken == null) {
            log.warn("Token 刷新返回空: path={}", requestPath);
            return Mono.empty();
        }

        String newJti = jwtUtil.getIdFromToken(refreshedToken);
        if (newJti == null) {
            log.warn("Token 刷新失败: 无法从新 Token 提取 JTI, path={}", requestPath);
            return Mono.empty();
        }

        Date expiration = jwtUtil.getExpirationDateFromToken(refreshedToken);
        if (expiration == null) {
            log.warn("Token 刷新失败: 无法读取新 Token 过期时间, path={}", requestPath);
            return Mono.empty();
        }

        String redisKey = TokenRedisKeys.jti(newJti);
        Duration ttl = Duration.ofMillis(expiration.getTime() - System.currentTimeMillis());

        return redisTemplate.opsForValue().setIfAbsent(redisKey, username != null ? username : "unknown", ttl)
                .timeout(Duration.ofSeconds(2))
                .flatMap(saved -> handleRefreshedTokenSaved(exchange, requestPath, authToken, refreshedToken, newJti, username, userId, saved))
                .doOnError(error -> log.warn("Token 刷新失败: 新 JTI 写入 Redis 异常, path={}, errorType={}",
                        requestPath, error.getClass().getSimpleName()))
                .then();
    }

    private Mono<Void> handleRefreshedTokenSaved(ServerWebExchange exchange,
                                           String requestPath,
                                           String authToken,
                                           String refreshedToken,
                                           String newJti,
                                           String username,
                                           Long userId,
                                           Boolean saved) {
        if (!Boolean.TRUE.equals(saved)) {
            log.warn("Token 刷新失败: 新 JTI 写入 Redis 失败或已存在");
            return Mono.empty();
        }

        String oldJti = jwtUtil.getIdFromToken(authToken);
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add("X-Refreshed-Token", refreshedToken);
        log.debug("Token 刷新成功: path={}", requestPath);

        Mono<Void> registerNewJti = userId == null
                ? Mono.empty()
                : redisTemplate.opsForSet().add(TokenRedisKeys.userJtis(userId), newJti)
                        .then(redisTemplate.expire(TokenRedisKeys.userJtis(userId),
                                Duration.ofMillis(jwtUtil.getExpirationDateFromToken(refreshedToken).getTime()
                                        - System.currentTimeMillis())))
                        .then();

        if (oldJti != null && !oldJti.equals(newJti)) {
            return registerNewJti.then(redisTemplate.delete(TokenRedisKeys.jti(oldJti)))
                    .then(userId == null
                            ? Mono.empty()
                            : redisTemplate.opsForSet().remove(TokenRedisKeys.userJtis(userId), oldJti).then())
                    .doOnSuccess(deleted -> {
                        log.debug("旧 JTI 已从 Redis 移除");
                    })
                    .doOnError(error -> log.warn("旧 JTI 清理失败: errorType={}", error.getClass().getSimpleName()))
                    .onErrorResume(error -> Mono.empty())
                    .then();
        }

        return registerNewJti;
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/users/register")
                || path.startsWith("/api/users/login")
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info");
    }
}
