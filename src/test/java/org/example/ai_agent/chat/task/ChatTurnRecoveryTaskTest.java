package org.example.ai_agent.chat.task;

import org.example.ai_agent.chat.config.ChatTurnRecoveryProperties;
import org.example.ai_agent.chat.service.ChatTurnLifecycle;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatTurnRecoveryTaskTest {

    @Test
    void scheduledRecoveryUsesAWindowLongerThanTheConfiguredStreamTimeout() {
        ChatTurnLifecycle lifecycle = mock(ChatTurnLifecycle.class);
        ChatTurnRecoveryProperties properties = new ChatTurnRecoveryProperties();
        properties.setStaleAfter(Duration.ofMinutes(2));
        when(lifecycle.recoverStaleTurns(Duration.ofMinutes(15), 200)).thenReturn(Mono.just(2));

        ChatTurnRecoveryTask task = new ChatTurnRecoveryTask(lifecycle, properties, Duration.ofMinutes(5).toMillis());
        task.recoverStaleTurns();

        verify(lifecycle, timeout(1000)).recoverStaleTurns(Duration.ofMinutes(15), 200);
    }
}
