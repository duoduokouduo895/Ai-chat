package org.example.ai_agent.chat.memory;

/** Memory disabled: the context loader keeps the newest-turns-only behaviour. */
public final class MemoryTestSupport {
    private MemoryTestSupport() {
    }

    public static ConversationMemoryService disabled() {
        var properties = new ConversationMemoryProperties();
        properties.setEnabled(false);
        return new ConversationMemoryService(null, properties);
    }
}
