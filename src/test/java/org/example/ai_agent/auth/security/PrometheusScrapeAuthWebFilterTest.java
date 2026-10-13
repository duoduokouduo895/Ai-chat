package org.example.ai_agent.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusScrapeAuthWebFilterTest {

    @Test
    void disabledFilterPassesPrometheusWithoutCredentials() {
        PrometheusScrapeAuthWebFilter filter = new PrometheusScrapeAuthWebFilter(false, "scrape", "secret");
        assertThat(filter.isAuthActive()).isFalse();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/prometheus").build());
        AtomicBoolean continued = new AtomicBoolean(false);
        WebFilterChain chain = ignored -> {
            continued.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(continued).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void enabledFilterRejectsMissingCredentials() {
        PrometheusScrapeAuthWebFilter filter = new PrometheusScrapeAuthWebFilter(true, "scrape", "secret");
        assertThat(filter.isAuthActive()).isTrue();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/prometheus").build());
        WebFilterChain chain = ignored -> Mono.error(new IllegalStateException("should not continue"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Basic realm=\"prometheus\"");
    }

    @Test
    void enabledFilterAcceptsMatchingBasicCredentials() {
        PrometheusScrapeAuthWebFilter filter = new PrometheusScrapeAuthWebFilter(true, "scrape", "secret");
        String encoded = Base64.getEncoder().encodeToString("scrape:secret".getBytes(StandardCharsets.UTF_8));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/prometheus")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                .build());
        AtomicBoolean continued = new AtomicBoolean(false);
        WebFilterChain chain = ignored -> {
            continued.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(continued).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void enabledFilterIgnoresNonPrometheusPaths() {
        PrometheusScrapeAuthWebFilter filter = new PrometheusScrapeAuthWebFilter(true, "scrape", "secret");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());
        AtomicBoolean continued = new AtomicBoolean(false);
        WebFilterChain chain = ignored -> {
            continued.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(continued).isTrue();
    }

    @Test
    void enabledWithoutCredentialsFailsFast() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new PrometheusScrapeAuthWebFilter(true, "", ""))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new PrometheusScrapeAuthWebFilter(true, "scrape", null))
                .isInstanceOf(IllegalStateException.class);
    }
}
