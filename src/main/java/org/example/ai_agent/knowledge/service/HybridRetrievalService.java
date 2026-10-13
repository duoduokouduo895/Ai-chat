package org.example.ai_agent.knowledge.service;

import reactor.core.publisher.Mono;

import java.util.List;

public interface HybridRetrievalService {

    Mono<org.example.ai_agent.knowledge.domain.RetrievalOutcome<RetrievalCandidate>> retrieveDetailed(
            String query, List<Long> knowledgeBaseIds, Integer maxResults, Double minSimilarity,
            Long requesterUserId, boolean isAdmin);

    Mono<List<RetrievalCandidate>> retrieve(String query, List<Long> knowledgeBaseIds,
                                            Integer maxResults, Double minSimilarity,
                                            Long requesterUserId, boolean isAdmin);

    record RetrievalCandidate(
            String embeddingId,
            Long knowledgeBaseId,
            Long documentId,
            Long versionId,
            Long chunkId,
            String content,
            String documentTitle,
            String knowledgeBaseName,
            Integer chunkIndex,
            double score
    ) {
    }
}
