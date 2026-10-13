package org.example.ai_agent.chat.memory;

import java.util.List;

/** Validated output of one compaction step. */
public record CompactionResult(String summary, int summaryTokens, List<NewConstraint> addConstraints,
                               List<Long> removeConstraintIds) {
    public CompactionResult {
        addConstraints = List.copyOf(addConstraints);
        removeConstraintIds = List.copyOf(removeConstraintIds);
    }

    public record NewConstraint(String kind, String content, long sourceSeq) {
    }
}
