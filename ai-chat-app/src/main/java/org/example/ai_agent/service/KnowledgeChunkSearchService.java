package org.example.ai_agent.service;

import org.example.ai_agent.document.KnowledgeChunkDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface KnowledgeChunkSearchService {
    Mono<Void> indexVersionChunks(Long documentId, Long versionId);
    Mono<Void> deleteVersionChunks(java.util.List<Long> chunkIds);

    Flux<KnowledgeChunkSearchHit> searchAuthorizedChunks(SearchRequest request);

    Mono<Void> indexDocumentChunks(Long documentId);

    Mono<Void> refreshKnowledgeBaseChunks(Long knowledgeBaseId);

    Mono<Void> deleteDocumentChunks(Long documentId);

    Mono<Void> deleteKnowledgeBaseChunks(Long knowledgeBaseId);

    Mono<Void> rebuildAllChunks();

    record SearchRequest(
            String query,
            List<Long> authorizedKnowledgeBaseIds,
            Long requesterUserId,
            boolean admin,
            int maxResults
    ) {
    }

    record KnowledgeChunkSearchHit(
            KnowledgeChunkDocument document,
            double score
    ) {
    }
}
