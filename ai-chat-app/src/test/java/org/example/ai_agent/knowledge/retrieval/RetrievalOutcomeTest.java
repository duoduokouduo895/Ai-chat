package org.example.ai_agent.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetrievalOutcomeTest {
    private static final RetrievalOutcome.Channel VECTOR_OK = new RetrievalOutcome.Channel(
            "Vector", RetrievalOutcome.ChannelStatus.SUCCEEDED, 1);
    private static final RetrievalOutcome.Channel BM25_FAILED = new RetrievalOutcome.Channel(
            "BM25", RetrievalOutcome.ChannelStatus.FAILED, 2);
    private static final RetrievalOutcome.Channel RERANK_DISABLED = new RetrievalOutcome.Channel(
            "Rerank", RetrievalOutcome.ChannelStatus.DISABLED, 0);

    @Test
    void allEnabledChannelsSucceedWithoutCandidatesIsEmpty() {
        assertEquals(RetrievalOutcome.Status.EMPTY,
                RetrievalOutcome.of(List.of(), List.of(VECTOR_OK, RERANK_DISABLED), 3).status());
    }

    @Test
    void aFailedChannelWithoutCandidatesIsUnavailable() {
        assertEquals(RetrievalOutcome.Status.UNAVAILABLE,
                RetrievalOutcome.of(List.of(), List.of(VECTOR_OK, BM25_FAILED), 3).status());
    }

    @Test
    void candidatesWithAChannelFailureAreDegraded() {
        assertEquals(RetrievalOutcome.Status.DEGRADED,
                RetrievalOutcome.of(List.of("evidence"), List.of(VECTOR_OK, BM25_FAILED), 3).status());
    }

    @Test
    void candidatesWithoutChannelFailureAreFound() {
        assertEquals(RetrievalOutcome.Status.FOUND,
                RetrievalOutcome.of(List.of("evidence"), List.of(VECTOR_OK, RERANK_DISABLED), 3).status());
    }
}
