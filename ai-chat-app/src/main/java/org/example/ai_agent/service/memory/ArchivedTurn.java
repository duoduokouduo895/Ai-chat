package org.example.ai_agent.service.memory;

/** An original turn that has been summarized and kept for exact recall. */
public record ArchivedTurn(long seq, String userText, String assistantText) {
}
