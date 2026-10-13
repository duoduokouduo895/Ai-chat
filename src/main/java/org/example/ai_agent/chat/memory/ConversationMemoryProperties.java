package org.example.ai_agent.chat.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "assistant.memory")
public class ConversationMemoryProperties {
    /** Disabled keeps the previous behaviour: newest whole turns only, nothing derived. */
    private boolean enabled = true;
    /** Compact when un-summarized history exceeds this share of the history budget. */
    private double triggerRatio = 0.6;
    /** Upper share of the input budget used by the memory block (constraints, summary, recall). */
    private double memoryMaxRatio = 0.3;
    /** Share of the history budget kept as original recent turns after compaction. */
    private double keepRecentRatio = 0.35;
    private int keepRecentMinTurns = 2;
    private int summaryMaxTokens = 800;
    private int maxConstraints = 20;
    private int recallMaxResults = 3;
    private int recallMaxChars = 1200;
    /** Soft cap of the redundant archive copy; oldest archived turns are evicted beyond it. */
    private DataSize archiveMaxSize = DataSize.ofGigabytes(10);
    private int summaryVersionsRetained = 5;
    private int maxAttempts = 5;
    private Duration retryBackoff = Duration.ofSeconds(30);
    private Duration failedCooldown = Duration.ofMinutes(30);
    private Duration modelTimeout = Duration.ofSeconds(90);
}
