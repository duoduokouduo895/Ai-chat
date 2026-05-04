package org.example.ai_agent.config.security;

import org.example.ai_agent.filter.TokenRefreshFilter;
import org.example.ai_agent.repository.auth.AdminUserRepository;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.TicketService;
import org.example.ai_agent.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
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
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final TokenRefreshFilter tokenRefreshFilter;
    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final TicketService ticketService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final List<String> corsAllowedOrigins;

    public SecurityConfig(JwtUtil jwtUtil,
                          TokenRefreshFilter tokenRefreshFilter,
                          UserRepository userRepository,
                          AdminUserRepository adminUserRepository,
                          TicketService ticketService,
                          ReactiveStringRedisTemplate redisTemplate,
                          @Value("${app.security.cors.allowed-origins}") String corsAllowedOrigins) {
        this.jwtUtil = jwtUtil;
        this.tokenRefreshFilter = tokenRefreshFilter;
        this.userRepository = userRepository;
        this.adminUserRepository = adminUserRepository;
        this.ticketService = ticketService;
        this.redisTemplate = redisTemplate;
        this.corsAllowedOrigins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toList());
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
                        .pathMatchers("/api/users/register", "/api/users/login", "/api/users/guest", "/api/test/**").permitAll()
                        .pathMatchers("/api/admin/auth/login").permitAll()
                        .pathMatchers("/api/admin/**", "/api/knowledge/admin/**", "/api/conversations/admin/**").hasRole("ADMIN")
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/api/diagnostic/**", "/actuator/**").hasRole("ADMIN")
                        .anyExchange().authenticated()
                )
                .addFilterAt(authFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(tokenRefreshFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public ReactiveAuthenticationManager combinedAuthenticationManager() {
        return authentication -> {
            if ("jwt".equals(authentication.getPrincipal())) {
                String token = (String) authentication.getCredentials();
                String username = jwtUtil.getUsernameFromToken(token);
                String jti = jwtUtil.getIdFromToken(token);
                String principalType = jwtUtil.getPrincipalTypeFromToken(token);

                if (username == null || jti == null) {
                    return Mono.error(new BadCredentialsException("Invalid token format"));
                }

                String redisKey = "token:jti:" + jti;
                return redisTemplate.hasKey(redisKey)
                        .flatMap(hasKey -> {
                            if (hasKey) {
                                if ("ADMIN".equalsIgnoreCase(principalType)) {
                                    return loadAuthenticatedAdmin(username, token);
                                }
                                return loadAuthenticatedUser(username, token);
                            }
                            return Mono.error(new BadCredentialsException("Token is invalid or revoked"));
                        });
            }

            if ("ticket".equals(authentication.getPrincipal())) {
                String ticket = (String) authentication.getCredentials();
                return ticketService.validateAndConsumeTicket(ticket)
                        .flatMap(username -> loadAuthenticatedUser(username, ticket))
                        .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid or expired ticket")));
            }

            return Mono.error(new BadCredentialsException("Unsupported authentication type"));
        };
    }

    private Mono<Authentication> loadAuthenticatedUser(String username, String credentials) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new BadCredentialsException("Authenticated user does not exist")))
                .map(user -> createAuthenticatedToken(user.getUsername(), user.getUserId(), credentials));
    }

    private Mono<Authentication> loadAuthenticatedAdmin(String username, String credentials) {
        return adminUserRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new BadCredentialsException("Authenticated admin does not exist")))
                .map(admin -> createAdminAuthenticationToken(admin.getUsername(), admin.getAdminId(), credentials));
    }

    private Authentication createAuthenticatedToken(String username, Long userId, String credentials) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                username,
                credentials,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        authenticationToken.setDetails(userId);
        return authenticationToken;
    }

    private Authentication createAdminAuthenticationToken(String username, Long adminId, String credentials) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                username,
                credentials,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        authenticationToken.setDetails(adminId);
        return authenticationToken;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "OPTIONS", "DELETE", "PUT"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setExposedHeaders(Arrays.asList("X-Refreshed-Token"));
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
                if (ticketParam != null && !ticketParam.isBlank()) {
                    return Mono.just(new UsernamePasswordAuthenticationToken("ticket", ticketParam));
                }
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String authToken = authHeader.substring(7).trim();
                if (!authToken.isBlank()) {
                    return Mono.just(new UsernamePasswordAuthenticationToken("jwt", authToken));
                }
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
