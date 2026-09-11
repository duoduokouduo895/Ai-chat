package org.example.ai_agent.knowledge.retrieval;

import java.util.List;

/** Channel failures remain distinguishable from a successful search without evidence. */
public record RetrievalOutcome<T>(Status status, List<T> candidates, List<Channel> channels, long durationMs) {
    public enum Status { FOUND, EMPTY, DEGRADED, UNAVAILABLE }
    public enum ChannelStatus { SUCCEEDED, FAILED, DISABLED }
    public record Channel(String name, ChannelStatus status, long durationMs) { }

    public RetrievalOutcome {
        candidates = List.copyOf(candidates);
        channels = List.copyOf(channels);
    }

    public static <T> RetrievalOutcome<T> of(List<T> candidates, List<Channel> channels, long durationMs) {
        boolean failed = channels.stream().anyMatch(channel -> channel.status() == ChannelStatus.FAILED);
        Status status = candidates.isEmpty() ? (failed ? Status.UNAVAILABLE : Status.EMPTY)
                : (failed ? Status.DEGRADED : Status.FOUND);
        return new RetrievalOutcome<>(status, candidates, channels, durationMs);
    }
}
