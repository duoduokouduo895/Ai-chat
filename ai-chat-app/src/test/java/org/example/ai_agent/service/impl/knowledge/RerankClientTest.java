package org.example.ai_agent.service.impl.knowledge;

import org.example.ai_agent.service.HybridRetrievalService.RetrievalCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RerankClientTest {

    @Test
    void enabledButUnconfiguredRerankIsAChannelFailure() {
        RerankClient client = new RerankClient();
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "baseUrl", "");
        ReflectionTestUtils.setField(client, "model", "");
        var candidate = candidate("chunk-1", 0.4);

        StepVerifier.create(client.rerankDetailed("query", List.of(candidate), 5))
                .assertNext(result -> {
                    assertEquals(List.of(candidate), result.candidates());
                    assertEquals(org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.ChannelStatus.FAILED,
                            result.channel().status());
                })
                .verifyComplete();
    }

    @Test
    void exhaustedBudgetSkipsRerankAndCapKeepsCallWithinBudget() throws Exception {
        // Accepts connections but never answers: an uncapped call would wait the full 3s timeout.
        try (var silentServer = new java.net.ServerSocket(0)) {
            RerankClient budgeted = new RerankClient();
            ReflectionTestUtils.setField(budgeted, "enabled", true);
            ReflectionTestUtils.setField(budgeted, "baseUrl", "http://127.0.0.1:" + silentServer.getLocalPort());
            ReflectionTestUtils.setField(budgeted, "model", "rerank-model");
            ReflectionTestUtils.setField(budgeted, "timeoutMs", 3000);
            ReflectionTestUtils.setField(budgeted, "topN", 20);
            var failed = org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.ChannelStatus.FAILED;

            StepVerifier.create(budgeted.rerankDetailed("query", candidates, 1, java.time.Duration.ofMillis(20)))
                    .assertNext(result -> {
                        assertEquals(List.of("first"), result.candidates().stream().map(RetrievalCandidate::content).toList());
                        assertEquals(failed, result.channel().status());
                    })
                    .expectComplete().verify(java.time.Duration.ofMillis(500));

            StepVerifier.create(budgeted.rerankDetailed("query", candidates, 2, java.time.Duration.ofMillis(300)))
                    .assertNext(result -> assertEquals(failed, result.channel().status()))
                    .expectComplete().verify(java.time.Duration.ofMillis(2000));
        }
    }

    private final RerankClient client = new RerankClient();
    private final List<RetrievalCandidate> candidates = List.of(
            candidate("first", 0.4),
            candidate("second", 0.8));

    @Test
    void applyScores_shouldUseSupportedFieldsAndSortByValidatedScore() {
        List<RetrievalCandidate> result = client.applyScores(candidates,
                "{\"results\":[{\"document_index\":0,\"relevance_score\":0.95},{\"index\":1,\"score\":0.75}]}",
                2);

        assertEquals(List.of("first", "second"), result.stream()
                .map(RetrievalCandidate::content)
                .toList());
        assertEquals(0.95, result.get(0).score());
    }

    @Test
    void applyScores_shouldFallbackForMalformedJsonOrInvalidResults() {
        assertEquals(List.of("first", "second"), client.applyScores(candidates, "not-json", 2)
                .stream().map(RetrievalCandidate::content).toList());
        assertEquals(List.of("first", "second"), client.applyScores(candidates,
                        "{\"results\":[{\"index\":8,\"score\":0.9}]}", 2)
                .stream().map(RetrievalCandidate::content).toList());
        assertEquals(List.of("first", "second"), client.applyScores(candidates,
                        "{\"results\":[]}", 2)
                .stream().map(RetrievalCandidate::content).toList());
    }

    private static RetrievalCandidate candidate(String content, double score) {
        return new RetrievalCandidate("embedding-" + content, 1L, 2L, null, 3L,
                content, "title", "knowledge-base", 0, score);
    }
}
