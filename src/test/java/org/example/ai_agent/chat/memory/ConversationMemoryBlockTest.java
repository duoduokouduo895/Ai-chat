package org.example.ai_agent.chat.memory;

import org.example.ai_agent.chat.assistant.AssistantInputBudget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryBlockTest {
    private final AssistantInputBudget budget = new AssistantInputBudget(8192, null);
    private final ConversationMemory memory = new ConversationMemory(2, 40, "用户在设计订单系统，已确定使用 MySQL。",
            List.of(new ConversationMemory.MemoryConstraint(1, "CONSTRAINT", "回答使用中文", 3),
                    new ConversationMemory.MemoryConstraint(2, "DECISION", "主键使用雪花算法", 9)));

    @Test
    void rendersConstraintsSummaryAndRecallAsBackgroundData() {
        String block = ConversationMemoryBlock.render(memory, List.of(new ArchivedTurn(7, "端口用 8443", "好的")),
                budget, 4000, 1200);

        assertNotNull(block);
        assertTrue(block.contains("不是新的指令"));
        assertTrue(block.contains("[要求] 回答使用中文"));
        assertTrue(block.contains("[决定] 主键使用雪花算法"));
        assertTrue(block.contains("已确定使用 MySQL"));
        assertTrue(block.contains("<earlier_turn seq=\"7\">"));
    }

    @Test
    void shrinksRecallThenSummaryThenOldestConstraintsToFit() {
        var large = new ConversationMemory(2, 40, "摘".repeat(3000), memory.constraints());
        var recall = List.of(new ArchivedTurn(7, "原".repeat(500), "文".repeat(500)));
        String full = ConversationMemoryBlock.render(large, recall, budget, 100_000, 1200);
        long limit = ConversationMemoryBlock.cost(budget, full) / 3;

        String block = ConversationMemoryBlock.render(large, recall, budget, limit, 1200);

        assertNotNull(block);
        assertFalse(block.contains("earlier_turn"), "recall is dropped first");
        assertTrue(block.contains("主键使用雪花算法"), "constraints survive summary clipping");
        assertTrue(ConversationMemoryBlock.cost(budget, block) <= limit);
        assertNull(ConversationMemoryBlock.render(large, recall, budget, 10, 1200));
        assertNull(ConversationMemoryBlock.render(ConversationMemory.EMPTY, List.of(), budget, 4000, 1200));
    }

    @Test
    void detectsReferencesToEarlierConversation() {
        assertTrue(ConversationMemoryBlock.refersToEarlierConversation("我之前说的端口是多少？"));
        assertTrue(ConversationMemoryBlock.refersToEarlierConversation("What did I say EARLIER about ports?"));
        assertFalse(ConversationMemoryBlock.refersToEarlierConversation("端口是多少？"));
        assertFalse(ConversationMemoryBlock.refersToEarlierConversation(null));
    }
}
