package org.example.ai_agent.knowledge.ingestion;

import java.time.LocalDateTime;

public record StageLeaseClaim(
        StageClaimDisposition disposition,
        String runId,
        IngestionStage requestedStage,
        IngestionStage currentStage,
        IngestionRunStatus currentStatus,
        String leaseToken,
        LocalDateTime leaseUntil) {

    public boolean isOwned() {
        return disposition == StageClaimDisposition.CLAIMED;
    }
}
