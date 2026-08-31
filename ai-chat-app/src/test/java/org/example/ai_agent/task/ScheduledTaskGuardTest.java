package org.example.ai_agent.task;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class ScheduledTaskGuardTest {

    @Test
    @Tag("minimal")
    void shouldRejectOverlappingRunUntilCurrentRunFinishes() {
        ScheduledTaskGuard guard = new ScheduledTaskGuard();

        org.junit.jupiter.api.Assertions.assertTrue(guard.tryStart());
        org.junit.jupiter.api.Assertions.assertFalse(guard.tryStart());

        guard.finish();

        org.junit.jupiter.api.Assertions.assertTrue(guard.tryStart());
    }
}
