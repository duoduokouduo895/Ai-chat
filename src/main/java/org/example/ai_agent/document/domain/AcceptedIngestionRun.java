package org.example.ai_agent.document.domain;

public record AcceptedIngestionRun(
        String runId,
        Long documentId,
        Long knowledgeBaseId,
        Long versionId,
        Long generation,
        IngestionStage firstStage,
        boolean newlyCreated) {
}
