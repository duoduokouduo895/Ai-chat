package org.example.ai_agent.chat.memory;

import java.util.List;

/**
 * A consistent read of derived memory: the summary covers every completed turn whose
 * sequence is at most {@code coveredThroughSeq}; later turns must come from the originals.
 */
public record ConversationMemory(int version, long coveredThroughSeq, String summary,
                                 List<MemoryConstraint> constraints) {
    public static final ConversationMemory EMPTY = new ConversationMemory(0, 0, null, List.of());

    public ConversationMemory {
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
    }

    public boolean hasArchive() {
        return coveredThroughSeq > 0;
    }

    public record MemoryConstraint(long id, String kind, String content, long sourceSeq) {
    }
}
