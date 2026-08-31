package org.example.ai_agent.conversation;

/** Durable lifecycle of one client chat request. */
public enum ChatTurnStatus {
    STARTED,
    STREAMING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
