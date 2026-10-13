package org.example.ai_agent.auth.security;

import org.example.ai_agent.auth.repository.AdminUserRepository;
import org.example.ai_agent.auth.repository.UserRepository;
import org.example.ai_agent.auth.service.TicketService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Verifies the Prometheus Basic Auth filter is wired into the real security chain. */
@WebFluxTest(controllers = PrometheusScrapeSecurityChainTest.MetricsStubController.class)
@Import({SecurityConfig.class, PrometheusScrapeSecurityChainTest.MetricsStubController.class})
@TestPropertySource(properties = {
        "app.security.cors.allowed-origins=http://localhost:3000",
        "app.security.actuator.prometheus-auth-enabled=true",
        "app.security.actuator.prometheus-username=scraper",
        "app.security.actuator.prometheus-password=s3cret-test"
})
@Tag("high-risk")
class PrometheusScrapeSecurityChainTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private AdminUserRepository adminUserRepository;
    @MockitoBean
    private TicketService ticketService;
    @MockitoBean
    private ReactiveStringRedisTemplate redisTemplate;

    @Test
    void prometheusRejectsRequestsWithoutCredentials() {
        webTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"prometheus\"");
    }

    @Test
    void prometheusRejectsWrongPassword() {
        webTestClient.get().uri("/actuator/prometheus")
                .header(HttpHeaders.AUTHORIZATION, basic("scraper", "wrong"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void prometheusAcceptsConfiguredCredentials() {
        webTestClient.get().uri("/actuator/prometheus")
                .header(HttpHeaders.AUTHORIZATION, basic("scraper", "s3cret-test"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("metrics");
    }

    @Test
    void otherActuatorEndpointsAreNotAffectedByScrapeCredentials() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
        webTestClient.get().uri("/actuator/metrics")
                .header(HttpHeaders.AUTHORIZATION, basic("scraper", "s3cret-test"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @RestController
    static class MetricsStubController {
        @GetMapping("/actuator/prometheus")
        String prometheus() {
            return "metrics";
        }

        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }

        @GetMapping("/actuator/metrics")
        String metrics() {
            return "metrics-index";
        }
    }
}
