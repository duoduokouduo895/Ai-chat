package org.example.ai_agent.filter;

import org.example.ai_agent.util.JwtUtil;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Date;

/**
 * JWT 令牌刷新过滤器 ("看门狗"机制)
 * <p>
 * 该过滤器在每个请求处理后执行，检查当前用户的JWT是否需要刷新。
 * 如果一个有效的JWT即将过期（在配置的刷新窗口期内），
 * 它会生成一个新的JWT并将其添加到响应头中，供客户端更新。
 * </p>
 */
@Component
public class TokenRefreshFilter implements WebFilter {

    private final JwtUtil jwtUtil;
    private final ReactiveStringRedisTemplate redisTemplate;

    public TokenRefreshFilter(JwtUtil jwtUtil, ReactiveStringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 先执行请求链，然后在响应中检查并刷新token
        return chain.filter(exchange)
                .then(Mono.defer(() -> {
                    // 请求处理完成后检查token刷新
                    return checkAndRefreshToken(exchange);
                }));
    }

    /**
     * 检查并刷新token
     */
    private Mono<Void> checkAndRefreshToken(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String requestPath = request.getPath().value();

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String authToken = authHeader.substring(7).trim(); // 去除token中的空白字符

            try {
                // 检查token是否需要刷新（包括即将过期的情况）
                if (jwtUtil.shouldRefreshToken(authToken)) {
                    String username = jwtUtil.getUsernameFromToken(authToken);
                    Long userId = jwtUtil.getUserIdFromToken(authToken);

                    // 尝试刷新token
                    String refreshedToken = jwtUtil.refreshToken(authToken);
                    if (refreshedToken != null) {
                        // 在给客户端返回新Token之前，必须先将新Token的JTI同步存储到Redis白名单
                        String newJti = jwtUtil.getIdFromToken(refreshedToken);
                        if (newJti != null) {
                            String redisKey = "token:jti:" + newJti;
                            Date expiration = jwtUtil.getExpirationDateFromToken(refreshedToken);
                            if (expiration != null) {
                                long ttlMillis = expiration.getTime() - System.currentTimeMillis();
                                Duration ttl = Duration.ofMillis(ttlMillis);
                                
                                // 使用响应式方式存储新JTI到Redis，确保在返回Token前完成存储
                                return redisTemplate.opsForValue().setIfAbsent(redisKey, username != null ? username : "unknown", ttl)
                                    .timeout(Duration.ofSeconds(2)) // 设置2秒超时
                                    .doOnSuccess(saved -> {
                                        if (Boolean.TRUE.equals(saved)) {
                                            System.out.println("🔄 [Token刷新] 新JTI已成功存储到Redis: " + newJti);
                                            
                                            // 清理旧Token的JTI（异步执行，不影响新Token返回）
                                            String oldJti = jwtUtil.getIdFromToken(authToken);
                                            if (oldJti != null && !oldJti.equals(newJti)) {
                                                String oldRedisKey = "token:jti:" + oldJti;
                                                redisTemplate.delete(oldRedisKey)
                                                    .doOnSuccess(deleted -> {
                                                        if (Boolean.TRUE.equals(deleted)) {
                                                            System.out.println("🗋 [Token清理] 旧JTI已从白名单中移除: " + oldJti);
                                                        }
                                                    })
                                                    .doOnError(error -> System.err.println("⚠️ [Token清理] 清理旧JTI异常: " + error.getMessage()))
                                                    .subscribe();
                                            }
                                            
                                            // 只有在JTI成功存储后才返回新Token
                                            ServerHttpResponse response = exchange.getResponse();
                                            response.getHeaders().add("X-Refreshed-Token", refreshedToken);
                                            
                                            // 详细的刷新成功日志
                                            System.out.println("🔄 [Token刷新] 用户 '" + username + "' (ID: " + userId + ") 的Token已成功刷新 | 请求路径: " + requestPath);

                                            // 记录刷新时间信息
                                            java.util.Date exp = jwtUtil.getExpirationDateFromToken(authToken);
                                            if (exp != null) {
                                                long timeToExpiry = exp.getTime() - System.currentTimeMillis();
                                                if (timeToExpiry > 0) {
                                                    System.out.println("📊 [Token刷新] 原Token剩余 " + (timeToExpiry / 1000 / 60) + " 分钟过期，提前刷新成功");
                                                } else {
                                                    System.out.println("📊 [Token刷新] 原Token已过期 " + (-timeToExpiry / 1000) + " 秒，宽限期内刷新成功");
                                                }
                                            }
                                        } else {
                                            System.err.println("⚠️ [Token刷新] 新JTI存储到Redis失败，JTI可能已存在: " + newJti + "，不返回新Token");
                                            // JTI存储失败时不返回新Token，让客户端继续使用原Token
                                        }
                                    })
                                    .doOnError(error -> {
                                        System.err.println("💥 [Token刷新] Redis存储新JTI异常: " + error.getMessage() + "，不返回新Token");
                                        // Redis异常时不返回新Token，让客户端继续使用原Token
                                    })
                                    .then(); // 转换为Mono<Void>
                            } else {
                                System.err.println("⚠️ [Token刷新] 无法获取新Token的过期时间，不返回新Token");
                                return Mono.empty();
                            }
                        } else {
                            System.err.println("⚠️ [Token刷新] 无法从新Token中提取JTI，不返回新Token");
                            return Mono.empty();
                        }
                    } else {
                        // 刷新失败但不抛异常
                        System.err.println("❌ [Token刷新] 用户 '" + username + "' Token刷新返回null | 请求路径: " + requestPath);
                        return Mono.empty();
                    }
                }
            } catch (Exception e) {
                // 增强的错误处理和日志
                String username = "unknown";
                try {
                    username = jwtUtil.getUsernameFromToken(authToken);
                } catch (Exception ignored) {
                    // 如果连用户名都获取不了，说明token完全无效
                }

                System.err.println("💥 [Token刷新异常] 用户: " + username + " | 请求路径: " + requestPath);
                System.err.println("💥 [Token刷新异常] 错误类型: " + e.getClass().getSimpleName());
                System.err.println("💥 [Token刷新异常] 错误消息: " + e.getMessage());

                // 记录token的部分信息用于调试（不记录完整token避免安全问题）
                if (authToken.length() > 20) {
                    String tokenPreview = authToken.substring(0, 10) + "..." + authToken.substring(authToken.length() - 10);
                    System.err.println("💥 [Token刷新异常] Token预览: " + tokenPreview);
                }

                // 不重新抛出异常，允许请求继续处理
                // 这样即使token刷新失败，原请求仍可能成功（如果token仍然有效）
            }
        } else {
            // 记录无Authorization头的情况（仅对需要认证的路径）
            if (!isPublicPath(requestPath)) {
                System.out.println("⚠️ [Token检查] 请求缺少Authorization头 | 请求路径: " + requestPath);
            }
        }
        return Mono.empty();
    }

    /**
     * 判断是否为公开路径（不需要认证的路径）
     */
    private boolean isPublicPath(String path) {
        return path.startsWith("/api/users/register") ||
               path.startsWith("/api/users/login") ||
               path.startsWith("/actuator") ||
               path.startsWith("/api/diagnostic") ||
               path.startsWith("/api/search");
    }
}
