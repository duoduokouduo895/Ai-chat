package org.example.ai_agent.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "assistant.chat-turn.recovery")
public class ChatTurnRecoveryProperties {

    private static final Duration MINIMUM_STALE_AFTER = Duration.ofMinutes(15);
    private static final Duration MAXIMUM_STALE_AFTER = Duration.ofHours(24);

    private Duration staleAfter = Duration.ofMinutes(15);
    private int batchSize = 200;

    public Duration effectiveStaleAfter(Duration minimumActiveLifetime) {
        Duration configured = boundedDuration(staleAfter, MINIMUM_STALE_AFTER, MAXIMUM_STALE_AFTER);
        if (minimumActiveLifetime == null || minimumActiveLifetime.isNegative() || minimumActiveLifetime.isZero()) {
            return configured;
        }
        return configured.compareTo(minimumActiveLifetime) >= 0 ? configured : minimumActiveLifetime;
    }

    public int effectiveBatchSize() {
        return Math.max(1, Math.min(batchSize, 1000));
    }

    private Duration boundedDuration(Duration value, Duration minimum, Duration maximum) {
        Duration candidate = value == null ? minimum : value;
        if (candidate.compareTo(minimum) < 0) {
            return minimum;
        }
        if (candidate.compareTo(maximum) > 0) {
            return maximum;
        }
        return candidate;
    }
}
