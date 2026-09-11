package org.example.ai_agent.service.impl.document;

public class IngestionLeaseLostException extends IllegalStateException {

    private final String runId;

    public IngestionLeaseLostException(String runId, String transition) {
        super("Ingestion lease was lost before transition " + transition + ": runId=" + runId);
        this.runId = runId;
    }

    public String getRunId() {
        return runId;
    }
}
