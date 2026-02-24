package org.example.ai_agent.config.security;

import org.example.ai_agent.filter.TokenRefreshFilter;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.TicketService;
import org.example.ai_agent.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final TokenRefreshFilter tokenRefreshFilter;
    private final UserRepository userRepository;
    private final TicketService ticketService;
    private final ReactiveStringRedisTemplate redisTemplate;

    public SecurityConfig(JwtUtil jwtUtil,
                          TokenRefreshFilter tokenRefreshFilter,
                          UserRepository userRepository,
                          TicketService ticketService,
                          ReactiveStringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.tokenRefreshFilter = tokenRefreshFilter;
        this.userRepository = userRepository;
        this.ticketService = ticketService;
        this.redisTemplate = redisTemplate;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        AuthenticationWebFilter authFilter = new AuthenticationWebFilter(combinedAuthenticationManager());
        authFilter.setServerAuthenticationConverter(combinedAuthenticationConverter());

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((swe, e) -> 
                                reactor.core.publisher.Mono.fromRunnable(() -> 
                                        swe.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED)
                                )
                        )
                )
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.POST, "/api/search/**").permitAll()
                        .pathMatchers("/api/users/register", "/api/users/login", "/api/search/**", "/api/test/**").permitAll()
                        .pathMatchers("/actuator/**").permitAll() // 开放 Actuator 端点访问
                        .pathMatchers("/api/diagnostic/**").permitAll() // 开放诊断端点访问
                        .anyExchange().authenticated()
                )
                .addFilterAt(authFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(tokenRefreshFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public ReactiveAuthenticationManager combinedAuthenticationManager() {
        return authentication -> {
            // --- JWT 认证流程 ---
            if ("jwt".equals(authentication.getPrincipal())) {
                String token = (String) authentication.getCredentials();
                String username = jwtUtil.getUsernameFromToken(token);
                String jti = jwtUtil.getIdFromToken(token);

                // 1. 基础验证：token 必须包含 username 和 jti
                if (username == null || jti == null) {
                    return Mono.error(new BadCredentialsException("无效的Token格式"));
                }

                // 2. Redis 白名单验证：jti 必须存在于 Redis 中
                String redisKey = "token:jti:" + jti;
                return redisTemplate.hasKey(redisKey)
                        .flatMap(hasKey -> {
                            if (hasKey) {
                                // 验证通过，创建认证对象
                                return Mono.just(createAuthenticatedToken(username, token));
                            } else {
                                // Redis 中不存在，说明 token 已被注销（例如，用户已登出）
                                return Mono.error(new BadCredentialsException("Token 已失效或已注销"));
                            }
                        });
            }
            // --- Ticket 认证流程 (保持不变) ---
            else if ("ticket".equals(authentication.getPrincipal())) {
                String ticket = (String) authentication.getCredentials();
                return ticketService.validateAndConsumeTicket(ticket)
                        .map(username -> createAuthenticatedToken(username, ticket))
                        .switchIfEmpty(Mono.error(new BadCredentialsException("无效或已过期的 Ticket")));
            }
            return Mono.error(new BadCredentialsException("不支持的认证类型"));
        };
    }

    private Authentication createAuthenticatedToken(String username, String credentials) {
        return new UsernamePasswordAuthenticationToken(
                username,
                credentials,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许本地开发和本地HTML文件访问
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",      // 前端开发服务器
            "http://dduo.chat",           // 生产环境域名
            "https://dduo.chat",          // 生产环境域名(HTTPS)
            "http://localhost:8080",      // 后端服务器
            "http://localhost:63342",     // IntelliJ IDEA内置Web服务器
            "null"                       // 允许file://协议的本地HTML文件
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "OPTIONS", "DELETE", "PUT"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setExposedHeaders(Arrays.asList("X-Refreshed-Token")); // 暴露刷新后的token头
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public ServerAuthenticationConverter combinedAuthenticationConverter() {
        return exchange -> {
            ServerHttpRequest request = exchange.getRequest();

            if (request.getURI().getPath().equals("/api/ai/chat")) {
                String ticketParam = request.getQueryParams().getFirst("ticket");
                if (ticketParam != null) {
                    return Mono.just(new UsernamePasswordAuthenticationToken("ticket", ticketParam));
                }
            }
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String authToken = authHeader.substring(7).trim(); // 去除token中的空白字符
                return Mono.just(new UsernamePasswordAuthenticationToken("jwt", authToken));
            }
            return Mono.empty();
        };
    }

    @Bean
    public ReactiveUserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .map(user -> User.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
                        .build());
    }
}
