package org.example.ai_agent.knowledge.ingestion;

public record AcceptedIngestionRun(
        String runId,
        Long documentId,
        Long knowledgeBaseId,
        Long versionId,
        Long generation,
        IngestionStage firstStage,
        boolean newlyCreated) {
}
