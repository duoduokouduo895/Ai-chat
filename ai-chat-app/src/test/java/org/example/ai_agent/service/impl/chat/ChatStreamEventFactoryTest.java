package org.example.ai_agent.service.impl.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("minimal")
class ChatStreamEventFactoryTest {

    private final ChatStreamEventFactory factory = new ChatStreamEventFactory(new ObjectMapper());

    @Test
    void complete_shouldUseDoneMarkerInsteadOfJsonPayload() {
        ServerSentEvent<String> event = factory.complete("turn-1");

        assertEquals("complete", event.event());
        assertEquals("turn-1", event.id());
        assertEquals("[DONE]", event.data());
        assertFalse(event.data().contains("turnId"));
    }
}
