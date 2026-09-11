package org.example.ai_agent.repository.document;

import org.example.ai_agent.entity.IngestionRunRow;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collection;

@Repository
public interface IngestionRunRepository extends ReactiveCrudRepository<IngestionRunRow, String> {

    Mono<IngestionRunRow> findByDocumentIdAndGeneration(Long documentId, Long generation);

    Mono<IngestionRunRow> findByVersionId(Long versionId);

    Mono<IngestionRunRow> findFirstByDocumentIdAndStatusInOrderByGenerationDesc(
            Long documentId, Collection<String> statuses);

    @Modifying
    @Query("""
            UPDATE ingestion_runs
            SET status = 'PROCESSING',
                attempt = attempt + 1,
                lease_token = :leaseToken,
                lease_until = :leaseUntil,
                started_at = COALESCE(started_at, :now),
                updated_at = :now
            WHERE id = :runId
              AND current_stage = :stage
              AND (
                    status IN ('PENDING', 'RETRY')
                    OR (status = 'PROCESSING' AND lease_until <= :now)
                  )
            """)
    Mono<Integer> claimStage(String runId, String stage, String leaseToken,
                             LocalDateTime leaseUntil, LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE ingestion_runs
            SET current_stage = :nextStage,
                status = 'PENDING',
                lease_token = NULL,
                lease_until = NULL,
                error_code = NULL,
                error_message = NULL,
                updated_at = :now
            WHERE id = :runId
              AND current_stage = :completedStage
              AND status = 'PROCESSING'
              AND lease_token = :leaseToken
              AND lease_until > :now
            """)
    Mono<Integer> advanceStage(String runId, String completedStage, String nextStage,
                               String leaseToken, LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE ingestion_runs
            SET status = 'COMPLETED',
                lease_token = NULL,
                lease_until = NULL,
                error_code = NULL,
                error_message = NULL,
                completed_at = :now,
                updated_at = :now
            WHERE id = :runId
              AND current_stage = :completedStage
              AND status = 'PROCESSING'
              AND lease_token = :leaseToken
              AND lease_until > :now
            """)
    Mono<Integer> completeRun(String runId, String completedStage, String leaseToken, LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE ingestion_runs
            SET status = 'RETRY',
                lease_token = NULL,
                lease_until = NULL,
                error_code = :errorCode,
                error_message = :errorMessage,
                updated_at = :now
            WHERE id = :runId
              AND current_stage = :stage
              AND status = 'PROCESSING'
              AND lease_token = :leaseToken
              AND lease_until > :now
            """)
    Mono<Integer> releaseForRetry(String runId, String stage, String leaseToken,
                                  String errorCode, String errorMessage, LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE ingestion_runs
            SET status = 'FAILED',
                lease_token = NULL,
                lease_until = NULL,
                error_code = :errorCode,
                error_message = :errorMessage,
                completed_at = :now,
                updated_at = :now
            WHERE id = :runId
              AND current_stage = :stage
              AND status = 'PROCESSING'
              AND lease_token = :leaseToken
              AND lease_until > :now
            """)
    Mono<Integer> failRun(String runId, String stage, String leaseToken,
                          String errorCode, String errorMessage, LocalDateTime now);
}
