package org.example.ai_agent.document.pipeline;

import org.example.ai_agent.document.config.DocumentProcessingProperties;
import org.example.ai_agent.document.dto.DocumentProcessingMessage;
import org.example.ai_agent.document.entity.IngestionRunRow;
import org.example.ai_agent.document.domain.IngestionRunStatus;
import org.example.ai_agent.document.domain.IngestionStage;
import org.example.ai_agent.document.domain.StageClaimDisposition;
import org.example.ai_agent.document.domain.StageLeaseClaim;
import org.example.ai_agent.document.repository.IngestionRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class IngestionRunLeaseService {

    private final IngestionRunRepository runRepository;
    private final DocumentProcessingProperties properties;
    private final Clock clock;

    @Autowired
    public IngestionRunLeaseService(IngestionRunRepository runRepository,
                                    DocumentProcessingProperties properties) {
        this(runRepository, properties, Clock.systemDefaultZone());
    }

    IngestionRunLeaseService(IngestionRunRepository runRepository,
                             DocumentProcessingProperties properties,
                             Clock clock) {
        this.runRepository = runRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public Mono<StageLeaseClaim> claim(DocumentProcessingMessage message) {
        VersionedMessage identity = VersionedMessage.from(message);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime leaseUntil = now.plus(properties.effectiveIngestionLeaseDuration());
        String leaseToken = UUID.randomUUID().toString();

        return runRepository.findById(identity.runId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Ingestion run does not exist: " + identity.runId())))
                .flatMap(run -> {
                    validateIdentity(run, identity);
                    return runRepository.claimStage(
                                    identity.runId(), identity.stage().name(), leaseToken, leaseUntil, now)
                            .flatMap(updated -> runRepository.findById(identity.runId())
                                    .switchIfEmpty(Mono.error(new IllegalStateException(
                                            "Ingestion run disappeared after claim: " + identity.runId())))
                                    .map(current -> updated != null && updated == 1
                                            ? claimed(current, identity.stage(), leaseToken, leaseUntil)
                                            : classifyUnclaimed(current, identity.stage(), now)));
                });
    }

    public Mono<Void> completeStage(StageLeaseClaim claim, IngestionStage nextStage) {
        requireOwned(claim);
        LocalDateTime now = LocalDateTime.now(clock);
        Mono<Integer> transition = nextStage == null
                ? runRepository.completeRun(
                        claim.runId(), claim.requestedStage().name(), claim.leaseToken(), now)
                : runRepository.advanceStage(
                        claim.runId(), claim.requestedStage().name(), nextStage.name(), claim.leaseToken(), now);
        return requireTransition(transition, claim.runId(), nextStage == null ? "COMPLETED" : nextStage.name());
    }

    public Mono<Void> releaseForRetry(StageLeaseClaim claim, String errorCode, String errorMessage) {
        requireOwned(claim);
        return requireTransition(runRepository.releaseForRetry(
                        claim.runId(), claim.requestedStage().name(), claim.leaseToken(),
                        normalizeErrorCode(errorCode), normalizeErrorMessage(errorMessage), LocalDateTime.now(clock)),
                claim.runId(), "RETRY");
    }

    public Mono<Void> fail(StageLeaseClaim claim, String errorCode, String errorMessage) {
        requireOwned(claim);
        return requireTransition(runRepository.failRun(
                        claim.runId(), claim.requestedStage().name(), claim.leaseToken(),
                        normalizeErrorCode(errorCode), normalizeErrorMessage(errorMessage), LocalDateTime.now(clock)),
                claim.runId(), "FAILED");
    }

    private StageLeaseClaim claimed(IngestionRunRow run, IngestionStage requestedStage,
                                    String leaseToken, LocalDateTime leaseUntil) {
        return new StageLeaseClaim(
                StageClaimDisposition.CLAIMED,
                run.getId(),
                requestedStage,
                IngestionStage.fromValue(run.getCurrentStage()),
                IngestionRunStatus.fromValue(run.getStatus()),
                leaseToken,
                leaseUntil);
    }

    private StageLeaseClaim classifyUnclaimed(IngestionRunRow run, IngestionStage requestedStage,
                                               LocalDateTime now) {
        IngestionStage currentStage = IngestionStage.fromValue(run.getCurrentStage());
        IngestionRunStatus status = IngestionRunStatus.fromValue(run.getStatus());
        StageClaimDisposition disposition;
        if (status == IngestionRunStatus.COMPLETED || currentStage.isAfter(requestedStage)) {
            disposition = StageClaimDisposition.ALREADY_COMPLETED;
        } else if (status == IngestionRunStatus.FAILED || status == IngestionRunStatus.CANCELLED) {
            disposition = StageClaimDisposition.RUN_FAILED;
        } else if (currentStage == requestedStage
                && status == IngestionRunStatus.PROCESSING
                && run.getLeaseToken() != null
                && run.getLeaseUntil() != null
                && run.getLeaseUntil().isAfter(now)) {
            disposition = StageClaimDisposition.LEASE_HELD;
        } else {
            disposition = StageClaimDisposition.REJECTED;
        }
        return new StageLeaseClaim(
                disposition,
                run.getId(),
                requestedStage,
                currentStage,
                status,
                null,
                run.getLeaseUntil());
    }

    private void validateIdentity(IngestionRunRow run, VersionedMessage message) {
        if (!Objects.equals(run.getDocumentId(), message.documentId())
                || !Objects.equals(run.getKnowledgeBaseId(), message.knowledgeBaseId())
                || !Objects.equals(run.getVersionId(), message.versionId())
                || !Objects.equals(run.getGeneration(), message.generation())) {
            throw new IllegalArgumentException("Ingestion message identity does not match run " + run.getId());
        }
    }

    private void requireOwned(StageLeaseClaim claim) {
        if (claim == null || !claim.isOwned() || claim.leaseToken() == null) {
            throw new IllegalArgumentException("An owned ingestion stage lease is required");
        }
    }

    private Mono<Void> requireTransition(Mono<Integer> transition, String runId, String target) {
        return transition
                .filter(updated -> updated != null && updated == 1)
                .switchIfEmpty(Mono.error(new IngestionLeaseLostException(runId, target)))
                .then();
    }

    private String normalizeErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return "INGESTION_ERROR";
        }
        String normalized = errorCode.replaceAll("[^A-Za-z0-9_.-]", "_");
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private String normalizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "Document ingestion failed";
        }
        return errorMessage.length() <= 2000 ? errorMessage : errorMessage.substring(0, 2000);
    }

    private record VersionedMessage(String runId, Long documentId, Long knowledgeBaseId,
                                    Long versionId, Long generation, IngestionStage stage) {

        static VersionedMessage from(DocumentProcessingMessage message) {
            if (message == null
                    || message.getRunId() == null || message.getRunId().isBlank()
                    || message.getDocumentId() == null
                    || message.getKnowledgeBaseId() == null
                    || message.getVersionId() == null
                    || message.getGeneration() == null) {
                throw new IllegalArgumentException("Versioned ingestion message is incomplete");
            }
            return new VersionedMessage(
                    message.getRunId(), message.getDocumentId(), message.getKnowledgeBaseId(),
                    message.getVersionId(), message.getGeneration(),
                    IngestionStage.fromValue(message.getProcessingType()));
        }
    }
}
