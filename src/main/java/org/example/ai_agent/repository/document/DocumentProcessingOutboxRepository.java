package org.example.ai_agent.repository.document;

import org.example.ai_agent.entity.DocumentProcessingOutboxEvent;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface DocumentProcessingOutboxRepository
        extends ReactiveCrudRepository<DocumentProcessingOutboxEvent, Long> {

    @Query("""
            SELECT * FROM document_processing_outbox
            WHERE status IN ('PENDING', 'RETRY')
              AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY id
            LIMIT :batchSize
            """)
    Flux<DocumentProcessingOutboxEvent> findDispatchable(@Param("batchSize") int batchSize);

    @Modifying
    @Query("""
            UPDATE document_processing_outbox
            SET status = 'SENDING', attempt_count = attempt_count + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
              AND status IN ('PENDING', 'RETRY')
              AND next_attempt_at <= CURRENT_TIMESTAMP
            """)
    Mono<Integer> claimForDispatch(@Param("id") Long id);

    @Modifying
    @Query("""
            UPDATE document_processing_outbox
            SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP, last_error = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id AND status = 'SENDING'
            """)
    Mono<Integer> markPublished(@Param("id") Long id);

    @Modifying
    @Query("""
            UPDATE document_processing_outbox
            SET status = 'RETRY', next_attempt_at = :nextAttemptAt, last_error = :lastError,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id AND status = 'SENDING'
            """)
    Mono<Integer> markRetry(@Param("id") Long id,
                            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                            @Param("lastError") String lastError);

    @Modifying
    @Query("""
            UPDATE document_processing_outbox
            SET status = 'RETRY', next_attempt_at = CURRENT_TIMESTAMP, last_error = 'dispatch claim timed out',
                updated_at = CURRENT_TIMESTAMP
            WHERE status = 'SENDING' AND updated_at < :staleBefore
            """)
    Mono<Integer> recoverStaleClaims(@Param("staleBefore") LocalDateTime staleBefore);
}
