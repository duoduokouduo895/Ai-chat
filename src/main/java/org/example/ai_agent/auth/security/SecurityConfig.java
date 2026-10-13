package org.example.ai_agent.auth.security;

import org.example.ai_agent.auth.repository.AdminUserRepository;
import org.example.ai_agent.auth.repository.UserRepository;
import org.example.ai_agent.auth.service.TicketService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final TicketService ticketService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final List<String> corsAllowedOrigins;
    private final boolean prometheusAuthEnabled;
    private final String prometheusUsername;
    private final String prometheusPassword;

    public SecurityConfig(JwtUtil jwtUtil,
                          UserRepository userRepository,
                          AdminUserRepository adminUserRepository,
                          TicketService ticketService,
                          ReactiveStringRedisTemplate redisTemplate,
                          @Value("${app.security.cors.allowed-origins}") String corsAllowedOrigins,
                          @Value("${app.security.actuator.prometheus-auth-enabled:false}") boolean prometheusAuthEnabled,
                          @Value("${app.security.actuator.prometheus-username:}") String prometheusUsername,
                          @Value("${app.security.actuator.prometheus-password:}") String prometheusPassword) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.adminUserRepository = adminUserRepository;
        this.ticketService = ticketService;
        this.redisTemplate = redisTemplate;
        this.corsAllowedOrigins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toList());
        this.prometheusAuthEnabled = prometheusAuthEnabled;
        this.prometheusUsername = prometheusUsername;
        this.prometheusPassword = prometheusPassword;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        AuthenticationWebFilter authFilter = new AuthenticationWebFilter(combinedAuthenticationManager());
        authFilter.setServerAuthenticationConverter(combinedAuthenticationConverter());
        PrometheusScrapeAuthWebFilter prometheusScrapeAuthWebFilter = new PrometheusScrapeAuthWebFilter(
                prometheusAuthEnabled, prometheusUsername, prometheusPassword);

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
                        .pathMatchers("/api/users/register", "/api/users/login", "/api/users/guest").permitAll()
                        .pathMatchers("/api/admin/auth/login").permitAll()
                        .pathMatchers("/api/admin/**", "/api/knowledge/admin/**", "/api/conversations/admin/**").hasRole("ADMIN")
                        .pathMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .pathMatchers("/api/diagnostic/**", "/actuator/**").hasRole("ADMIN")
                        .anyExchange().authenticated()
                )
                .addFilterBefore(prometheusScrapeAuthWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAt(authFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterBefore(new org.example.ai_agent.auth.security.TokenRefreshFilter(
                        jwtUtil, redisTemplate, userRepository), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public ReactiveAuthenticationManager combinedAuthenticationManager() {
        return authentication -> {
            if ("jwt".equals(authentication.getPrincipal())) {
                String token = (String) authentication.getCredentials();
                JwtUtil.AuthenticationClaims claims;
                try {
                    claims = jwtUtil.parseAuthenticationClaims(token);
                } catch (JwtUtil.InvalidAuthenticationTokenException error) {
                    return Mono.error(new BadCredentialsException("Token is expired or invalid", error));
                }

                String redisKey = TokenRedisKeys.jti(claims.jti());
                return redisTemplate.hasKey(redisKey)
                        .flatMap(hasKey -> {
                            if (hasKey) {
                                if ("ADMIN".equals(claims.principalType())) {
                                    return authenticateActiveAdmin(
                                            claims.username(), claims.adminId(), token);
                                }

                                return authenticateActiveUser(
                                        claims.username(), claims.userId(), token, claims.guest());
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
                .map(user -> createAuthenticatedToken(user.getUsername(), user.getUserId(), credentials,
                        Boolean.TRUE.equals(user.getIsGuest())));
    }

    private Mono<Authentication> authenticateActiveUser(String username, Long userId, String credentials,
                                                        boolean guest) {
        return redisTemplate.hasKey(TokenRedisKeys.userRevoked(userId))
                .flatMap(revoked -> Boolean.TRUE.equals(revoked)
                        ? Mono.error(new BadCredentialsException("User token has been revoked"))
                        : Mono.just(createAuthenticatedToken(username, userId, credentials,
                                guest)));
    }

    private Mono<Authentication> authenticateActiveAdmin(String username, Long adminId, String credentials) {
        return adminUserRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new BadCredentialsException("Authenticated admin does not exist")))
                .flatMap(admin -> {
                    if (!"ACTIVE".equalsIgnoreCase(admin.getStatus())) {
                        return Mono.error(new BadCredentialsException("Admin account is disabled"));
                    }
                    Long resolvedAdminId = admin.getAdminId() != null ? admin.getAdminId() : adminId;
                    return Mono.just(createAdminAuthenticationToken(
                            admin.getUsername(), resolvedAdminId, credentials));
                });
    }

    private Authentication createAuthenticatedToken(String username, Long userId, String credentials, boolean guest) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (guest) {
            authorities.add(new SimpleGrantedAuthority("ROLE_GUEST"));
        }
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                username,
                credentials,
                authorities
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
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "Origin"));
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
