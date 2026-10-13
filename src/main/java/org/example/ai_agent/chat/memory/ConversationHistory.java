package org.example.ai_agent.chat.memory;

import org.example.ai_agent.chat.domain.CompletedContextTurn;

import java.util.List;

/** Derived memory plus the original turns it does not cover, oldest first. */
public record ConversationHistory(ConversationMemory memory, List<CompletedContextTurn> turns) {
    public static final ConversationHistory EMPTY = new ConversationHistory(ConversationMemory.EMPTY, List.of());

    public ConversationHistory {
        memory = memory == null ? ConversationMemory.EMPTY : memory;
        turns = List.copyOf(turns);
    }

    public static ConversationHistory of(List<CompletedContextTurn> turns) {
        return new ConversationHistory(ConversationMemory.EMPTY, turns);
    }
}
