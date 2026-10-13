package org.example.ai_agent.document.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 文档异步处理的硬资源边界。
 * <p>
 * 这些值即使被配置中心覆盖，也会在代码中限制在安全区间内，避免错误配置扩大
 * 解析器、AI 增强或消息发布对应用进程的影响范围。
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge-base.processing")
public class DocumentProcessingProperties {

    private Duration parseTimeout = Duration.ofSeconds(90);
    private Duration aiEnhancementTimeout = Duration.ofSeconds(90);
    private Duration consumerTimeout = Duration.ofMinutes(5);
    private Duration publishConfirmTimeout = Duration.ofSeconds(5);
    private int parserMaxThreads = 2;
    private int parserQueueCapacity = 20;
    private int maxExtractedCharacters = 1_000_000;
    private int maxAiEnhancementCharacters = 50_000;
    private int outboxBatchSize = 20;
    private Duration outboxClaimTimeout = Duration.ofMinutes(2);
    private Duration outboxRetryDelay = Duration.ofSeconds(5);
    private Duration outboxMaxRetryDelay = Duration.ofMinutes(5);
    private Duration ingestionLeaseDuration = Duration.ofMinutes(5);
    private boolean versionedIngestionEnabled;

    public Duration effectiveParseTimeout() {
        return boundedDuration(parseTimeout, Duration.ofSeconds(1), Duration.ofMinutes(3));
    }

    public Duration effectiveAiEnhancementTimeout() {
        return boundedDuration(aiEnhancementTimeout, Duration.ofSeconds(1), Duration.ofMinutes(3));
    }

    public Duration effectiveConsumerTimeout() {
        return boundedDuration(consumerTimeout, Duration.ofSeconds(1), Duration.ofMinutes(10));
    }

    public Duration effectivePublishConfirmTimeout() {
        return boundedDuration(publishConfirmTimeout, Duration.ofSeconds(1), Duration.ofSeconds(30));
    }

    public int effectiveParserMaxThreads() {
        return Math.max(1, Math.min(parserMaxThreads, 4));
    }

    public int effectiveParserQueueCapacity() {
        return Math.max(1, Math.min(parserQueueCapacity, 100));
    }

    public int effectiveMaxExtractedCharacters() {
        return Math.max(10_000, Math.min(maxExtractedCharacters, 2_000_000));
    }

    public int effectiveMaxAiEnhancementCharacters() {
        return Math.max(5_000, Math.min(maxAiEnhancementCharacters, 100_000));
    }

    public int effectiveOutboxBatchSize() {
        return Math.max(1, Math.min(outboxBatchSize, 100));
    }

    public Duration effectiveOutboxClaimTimeout() {
        return boundedDuration(outboxClaimTimeout, Duration.ofSeconds(10), Duration.ofMinutes(15));
    }

    public Duration effectiveOutboxRetryDelay() {
        return boundedDuration(outboxRetryDelay, Duration.ofSeconds(1), Duration.ofMinutes(1));
    }

    public Duration effectiveOutboxMaxRetryDelay() {
        return boundedDuration(outboxMaxRetryDelay, Duration.ofSeconds(10), Duration.ofMinutes(30));
    }

    public Duration effectiveIngestionLeaseDuration() {
        return boundedDuration(ingestionLeaseDuration, Duration.ofSeconds(10), Duration.ofMinutes(10));
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
