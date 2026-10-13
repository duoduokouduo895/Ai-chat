package org.example.ai_agent.chat.task;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.chat.config.ChatTurnRecoveryProperties;
import org.example.ai_agent.chat.service.ChatTurnLifecycle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import org.example.ai_agent.common.scheduling.ScheduledTaskGuard;

/** Periodically terminates abandoned ChatTurn rows so duplicate requests do not remain permanently in progress. */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "assistant.chat-turn.recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ChatTurnRecoveryTask {

    private final ChatTurnLifecycle chatTurnLifecycle;
    private final ChatTurnRecoveryProperties properties;
    private final Duration minimumActiveLifetime;
    @Value("${assistant.chat-turn.max-duration-ms:300000}")
    private long maxTurnDurationMs = 300000;
    private final ScheduledTaskGuard taskGuard = new ScheduledTaskGuard();

    public ChatTurnRecoveryTask(ChatTurnLifecycle chatTurnLifecycle,
                                ChatTurnRecoveryProperties properties,
                                @Value("${assistant.stream.timeout-ms:60000}") long streamTimeoutMs) {
        this.chatTurnLifecycle = chatTurnLifecycle;
        this.properties = properties;
        this.minimumActiveLifetime = Duration.ofMillis(Math.max(1L, streamTimeoutMs)).plusMinutes(1);
    }

    @Scheduled(
            fixedDelayString = "${assistant.chat-turn.recovery.interval-ms:60000}",
            initialDelayString = "${assistant.chat-turn.recovery.initial-delay-ms:60000}")
    public void recoverStaleTurns() {
        if (!taskGuard.tryStart()) {
            log.debug("ChatTurn 恢复任务上一轮尚未结束，跳过本轮");
            return;
        }

        Duration wholeTurnWindow = Duration.ofMillis(Math.max(1L, maxTurnDurationMs)).plusMinutes(1);
        Duration staleAfter = properties.effectiveStaleAfter(
                wholeTurnWindow.compareTo(minimumActiveLifetime) > 0 ? wholeTurnWindow : minimumActiveLifetime);
        chatTurnLifecycle.recoverStaleTurns(staleAfter, properties.effectiveBatchSize())
                .doOnError(failure -> log.error("ChatTurn 恢复任务失败: errorType={}",
                        failure.getClass().getSimpleName()))
                .onErrorResume(ignored -> Mono.empty())
                .doFinally(signal -> taskGuard.finish())
                .subscribe();
    }
}
