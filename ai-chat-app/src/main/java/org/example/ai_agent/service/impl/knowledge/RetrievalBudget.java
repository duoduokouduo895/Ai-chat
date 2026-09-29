package org.example.ai_agent.service.impl.knowledge;

import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.time.Duration;
import java.util.Optional;

/**
 * Latency budget of one chat turn's pre-generation retrieval, carried in the Reactor Context.
 * Only optional steps (query rewriting, rerank) spend from it; recall channels keep their own
 * timeouts because evidence outranks latency. Callers without a budget keep the configured timeouts.
 */
public final class RetrievalBudget {

    private static final Class<RetrievalBudget> KEY = RetrievalBudget.class;

    private final long deadlineNanos;

    private RetrievalBudget(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    /** Starts the budget when the pipeline is subscribed. */
    public static Context start(Context context, Duration budget) {
        return context.put(KEY, new RetrievalBudget(System.nanoTime() + budget.toNanos()));
    }

    /** Time left in the budget, never negative; empty when the caller set no budget. */
    public static Optional<Duration> remaining(ContextView context) {
        return context.<RetrievalBudget>getOrEmpty(KEY)
                .map(budget -> Duration.ofNanos(Math.max(0L, budget.deadlineNanos - System.nanoTime())));
    }
}
