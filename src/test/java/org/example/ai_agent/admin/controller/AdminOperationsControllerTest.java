package org.example.ai_agent.admin.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.example.ai_agent.admin.operations.AidiagClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class AdminOperationsControllerTest {

    private AidiagClient client;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(AidiagClient.class);
        webTestClient = WebTestClient.bindToController(new AdminOperationsController(client)).build();
    }

    @Test
    void forwardsOnlyBoundedIncidentFilters() {
        when(client.incidents(0, 20, "RESOLVED", "WARNING"))
                .thenReturn(Mono.just(new AidiagClient.IncidentPage(List.of(), 0, 20, 0)));

        webTestClient.get().uri("/api/admin/operations/incidents?status=RESOLVED&severity=WARNING")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(0);

        verify(client).incidents(0, 20, "RESOLVED", "WARNING");
    }

    @Test
    void rejectsUnknownStatusBeforeCallingAidiag() {
        webTestClient.get().uri("/api/admin/operations/incidents?status=UNKNOWN")
                .exchange().expectStatus().isBadRequest();
    }
}
