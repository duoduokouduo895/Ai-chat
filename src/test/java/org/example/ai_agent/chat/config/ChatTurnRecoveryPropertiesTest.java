package org.example.ai_agent.chat.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatTurnRecoveryPropertiesTest {

    @Test
    void staleWindowMustNotBeShorterThanTheLongestActiveRequest() {
        ChatTurnRecoveryProperties properties = new ChatTurnRecoveryProperties();
        properties.setStaleAfter(Duration.ofMinutes(2));

        assertEquals(Duration.ofMinutes(15),
                properties.effectiveStaleAfter(Duration.ofMinutes(6)));
    }

    @Test
    void configuredStaleWindowIsBoundedToASafeRange() {
        ChatTurnRecoveryProperties properties = new ChatTurnRecoveryProperties();
        properties.setStaleAfter(Duration.ofSeconds(1));
        assertEquals(Duration.ofMinutes(15), properties.effectiveStaleAfter(Duration.ofMinutes(1)));

        properties.setStaleAfter(Duration.ofDays(7));
        assertEquals(Duration.ofHours(24), properties.effectiveStaleAfter(Duration.ofMinutes(1)));
    }

    @Test
    void recoveryBatchSizeIsBounded() {
        ChatTurnRecoveryProperties properties = new ChatTurnRecoveryProperties();
        properties.setBatchSize(0);
        assertEquals(1, properties.effectiveBatchSize());

        properties.setBatchSize(5000);
        assertEquals(1000, properties.effectiveBatchSize());
    }
}
