package org.example.ai_agent.knowledge.domain;

import java.util.List;

/** Durable references contain identifiers only; displaying them requires a fresh authorization check. */
public record RetrievalSnapshot(RetrievalOutcome.Status status, List<RetrievalOutcome.Channel> channels,
                                List<Source> sources) {
    public record Source(Long knowledgeBaseId, Long documentId, Long versionId, Long chunkId) { }
    public RetrievalSnapshot {
        channels = List.copyOf(channels);
        sources = List.copyOf(sources);
    }
}
