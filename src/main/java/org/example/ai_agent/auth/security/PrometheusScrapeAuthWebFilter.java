package org.example.ai_agent.auth.security;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Optional Basic Auth for {@code /actuator/prometheus} only.
 * Disabled by default so local/CI and network-trust scrapes keep working.
 * Enable together with ai-ops Prometheus {@code AI_CHAT_PROMETHEUS_*} credentials.
 */
public class PrometheusScrapeAuthWebFilter implements WebFilter {

    static final String PROMETHEUS_PATH = "/actuator/prometheus";

    private final boolean authEnabled;
    private final String expectedUsername;
    private final byte[] expectedPasswordBytes;

    public PrometheusScrapeAuthWebFilter(boolean authEnabled, String username, String password) {
        if (authEnabled && (username == null || username.isBlank() || password == null || password.isBlank())) {
            throw new IllegalStateException(
                    "app.security.actuator.prometheus-auth-enabled=true requires non-blank "
                            + "prometheus-username and prometheus-password");
        }
        this.authEnabled = authEnabled;
        this.expectedUsername = username == null ? "" : username;
        this.expectedPasswordBytes = password == null
                ? new byte[0]
                : password.getBytes(StandardCharsets.UTF_8);
    }

    boolean isAuthActive() {
        return authEnabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!authEnabled || !isPrometheusPath(exchange.getRequest())) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return unauthorized(exchange);
        }

        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return unauthorized(exchange);
        }

        int separator = decoded.indexOf(':');
        if (separator < 0) {
            return unauthorized(exchange);
        }

        String username = decoded.substring(0, separator);
        byte[] passwordBytes = decoded.substring(separator + 1).getBytes(StandardCharsets.UTF_8);
        boolean usernameOk = MessageDigest.isEqual(
                expectedUsername.getBytes(StandardCharsets.UTF_8),
                username.getBytes(StandardCharsets.UTF_8));
        boolean passwordOk = MessageDigest.isEqual(expectedPasswordBytes, passwordBytes);
        if (!usernameOk || !passwordOk) {
            return unauthorized(exchange);
        }

        return chain.filter(exchange);
    }

    private static boolean isPrometheusPath(ServerHttpRequest request) {
        String path = request.getPath().pathWithinApplication().value();
        return PROMETHEUS_PATH.equals(path);
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"prometheus\"");
        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
        byte[] body = "Unauthorized".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
