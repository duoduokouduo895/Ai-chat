package org.example.ai_agent.filter;

import org.example.ai_agent.util.JwtUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

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

    public TokenRefreshFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 1. 在请求处理前检查并刷新token
        return ReactiveSecurityContextHolder.getContext()
                .cast(Object.class)
                .switchIfEmpty(Mono.defer(() -> {
                    // 如果没有安全上下文，尝试从请求头中检查token
                    return checkAndRefreshToken(exchange).then(Mono.empty());
                }))
                .then(Mono.defer(() -> {
                    // 如果有安全上下文，也检查token刷新
                    return ReactiveSecurityContextHolder.getContext()
                            .filter(context -> context.getAuthentication() != null && context.getAuthentication().isAuthenticated())
                            .flatMap(context -> checkAndRefreshToken(exchange))
                            .switchIfEmpty(Mono.empty());
                }))
                // 2. 然后继续执行请求链
                .then(chain.filter(exchange));
    }
    
    /**
     * 检查并刷新token
     */
    private Mono<Void> checkAndRefreshToken(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String authToken = authHeader.substring(7).trim(); // 去除token中的空白字符
            
            // 检查token是否需要刷新（包括即将过期的情况）
            if (jwtUtil.shouldRefreshToken(authToken)) {
                try {
                    String refreshedToken = jwtUtil.refreshToken(authToken);
                    if (refreshedToken != null) {
                        ServerHttpResponse response = exchange.getResponse();
                        response.getHeaders().add("X-Refreshed-Token", refreshedToken);
                        
                        // 获取用户名用于日志
                        String username = jwtUtil.getUsernameFromToken(authToken);
                        System.out.println("Token for user '" + username + "' has been refreshed proactively.");
                    }
                } catch (Exception e) {
                    System.err.println("Failed to refresh token: " + e.getMessage());
                }
            }
        }
        return Mono.empty();
    }
}
