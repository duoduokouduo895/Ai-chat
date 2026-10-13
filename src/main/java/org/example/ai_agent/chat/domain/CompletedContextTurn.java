package org.example.ai_agent.chat.domain;

import java.time.LocalDateTime;

/** A committed pair; failed or partially generated turns never satisfy this contract. */
public record CompletedContextTurn(String turnId, String userText, String assistantText,
                                   LocalDateTime completedAt, Long sequenceId) {
    public CompletedContextTurn(String turnId, String userText, String assistantText, LocalDateTime completedAt) {
        this(turnId, userText, assistantText, completedAt, 0L);
    }
}
