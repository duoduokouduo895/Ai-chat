package org.example.ai_agent.document.repository;

import org.example.ai_agent.document.entity.DocumentVersionRow;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface DocumentVersionRepository extends ReactiveCrudRepository<DocumentVersionRow, Long> {

    Mono<DocumentVersionRow> findByDocumentIdAndGeneration(Long documentId, Long generation);

    Mono<DocumentVersionRow> findFirstByDocumentIdAndActivationStatusOrderByGenerationDesc(
            Long documentId, String activationStatus);
}
