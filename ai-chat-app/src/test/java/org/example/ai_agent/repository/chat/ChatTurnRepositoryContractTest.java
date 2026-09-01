package org.example.ai_agent.repository.chat;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.repository.Modifying;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatTurnRepositoryContractTest {

    @Test
    void lifecycleStateTransitionsMustBeDeclaredAsModifyingQueries() throws NoSuchMethodException {
        assertModifying("markStreaming", String.class);
        assertModifying("markCompleted", String.class, Long.class);
        assertModifying("markFailed", String.class, String.class);
        assertModifying("markCancelled", String.class);
    }

    private void assertModifying(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        assertTrue(ChatTurnRepository.class.getMethod(methodName, parameterTypes)
                        .isAnnotationPresent(Modifying.class),
                () -> methodName + " must emit the R2DBC update count for ChatTurn lifecycle transitions");
    }
}
