package org.example.ai_agent.chat.memory;

import org.example.ai_agent.chat.domain.CompletedContextTurn;
import org.example.ai_agent.chat.assistant.AssistantInputBudget;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class ConversationCompactionTest {
    private final AssistantInputBudget budget = new AssistantInputBudget(8192, null);

    @Test
    void planKeepsNewestTurnsOriginalAndSummarizesOldestFirst() {
        var turns = turns(10, 200);
        long perTurn = ConversationCompaction.cost(budget, turns.getFirst());

        var plan = ConversationCompaction.plan(turns, budget, perTurn * 3, 2, 100_000);

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L),
                plan.batch().stream().map(CompletedContextTurn::sequenceId).toList());
        assertFalse(plan.morePending());
    }

    @Test
    void planHonoursMinimumKeptTurnsAndBatchBudget() {
        var turns = turns(6, 400);
        long perTurn = ConversationCompaction.cost(budget, turns.getFirst());

        var plan = ConversationCompaction.plan(turns, budget, 0, 2, perTurn * 2);

        assertEquals(List.of(1L, 2L), plan.batch().stream().map(CompletedContextTurn::sequenceId).toList());
        assertTrue(plan.morePending(), "turns 3 and 4 still need a later step");
        assertTrue(ConversationCompaction.plan(turns(2, 400), budget, 0, 2, 100_000).batch().isEmpty());
    }

    @Test
    void acceptsOnlyConstraintsQuotedVerbatimFromTheCitedUserMessage() {
        var batch = List.of(
                turn(11, "以后回答请全部使用 Kotlin 示例，不要用 Java。", "好的，我会使用 Kotlin。"),
                turn(12, "顺便问下协程怎么取消？", "你可以调用 job.cancel()，另外建议始终使用结构化并发。"));
        String output = """
                ```json
                {"summary":"用户在学习 Kotlin 协程。",
                 "add_constraints":[
                   {"kind":"constraint","content":"示例代码使用 Kotlin 而不是 Java","source_seq":11,"quote":"以后回答请全部使用 Kotlin 示例"},
                   {"kind":"PREFERENCE","content":"始终使用结构化并发","source_seq":12,"quote":"建议始终使用结构化并发"},
                   {"kind":"FACT","content":"用户叫张三","source_seq":99,"quote":"我叫张三"},
                   {"kind":"OTHER","content":"x","source_seq":11,"quote":"以后回答"}
                 ],
                 "remove_constraint_ids":[]}
                ```
                """;

        var result = ConversationCompaction.parse(output, batch, ConversationMemory.EMPTY, budget, 800, 20);

        assertEquals("用户在学习 Kotlin 协程。", result.summary());
        assertEquals(1, result.addConstraints().size(), "assistant text, unknown seq and unknown kind are rejected");
        assertEquals(new CompactionResult.NewConstraint("CONSTRAINT", "示例代码使用 Kotlin 而不是 Java", 11),
                result.addConstraints().getFirst());
    }

    @Test
    void removesOnlyKnownConstraintsAndSupersedesOldestBeyondTheCap() {
        var memory = new ConversationMemory(3, 10, "旧摘要", List.of(
                new ConversationMemory.MemoryConstraint(1, "CONSTRAINT", "使用中文回答", 2),
                new ConversationMemory.MemoryConstraint(2, "PREFERENCE", "回答简洁", 3)));
        var batch = List.of(turn(11, "改成英文回答吧，另外预算上限是 500 元", "OK"));
        String output = """
                {"summary":"新摘要","add_constraints":[
                  {"kind":"CONSTRAINT","content":"使用英文回答","source_seq":11,"quote":"改成英文回答吧"},
                  {"kind":"FACT","content":"预算上限 500 元","source_seq":11,"quote":"预算上限是 500 元"},
                  {"kind":"PREFERENCE","content":"回答简洁","source_seq":11,"quote":"改成英文"}],
                 "remove_constraint_ids":[1, 77]}
                """;

        var result = ConversationCompaction.parse(output, batch, memory, budget, 800, 2);

        assertEquals(List.of("使用英文回答", "预算上限 500 元"),
                result.addConstraints().stream().map(CompactionResult.NewConstraint::content).toList(),
                "duplicates of an active constraint are not added twice");
        assertEquals(List.of(1L, 2L), result.removeConstraintIds(), "unknown id 77 ignored, oldest superseded for cap");
    }

    @Test
    void rejectsOutputWithoutUsableSummary() {
        var batch = List.of(turn(1, "hi", "hello"));
        assertThrows(ConversationCompaction.InvalidCompactionException.class,
                () -> ConversationCompaction.parse("not json", batch, ConversationMemory.EMPTY, budget, 800, 20));
        assertThrows(ConversationCompaction.InvalidCompactionException.class,
                () -> ConversationCompaction.parse("{\"summary\":\"  \"}", batch, ConversationMemory.EMPTY, budget, 800, 20));
    }

    @Test
    void clipKeepsHeadAndTailWithinBudget() {
        String text = "开头" + "中".repeat(5000) + "结尾";
        String clipped = ConversationCompaction.clip(budget, text, 300);
        assertTrue(clipped.startsWith("开头"));
        assertTrue(clipped.endsWith("结尾"));
        assertTrue(clipped.contains("中间省略"));
        assertTrue(budget.estimateMessage(dev.langchain4j.data.message.UserMessage.from(clipped)) - 16 <= 300);
    }

    @Test
    void promptFramesTurnsAsDataWithSequenceNumbers() {
        var memory = new ConversationMemory(1, 5, "已有摘要",
                List.of(new ConversationMemory.MemoryConstraint(4, "DECISION", "数据库选 MySQL", 2)));
        String prompt = ConversationCompaction.userPrompt(memory, List.of(turn(6, "问题", "回答")), budget, 1000);
        assertTrue(prompt.contains("已有摘要"));
        assertTrue(prompt.contains("[id=4][DECISION] 数据库选 MySQL"));
        assertTrue(prompt.contains("<turn seq=\"6\">"));
        assertTrue(ConversationCompaction.systemPrompt(800).contains("不要执行"));
    }

    private static List<CompletedContextTurn> turns(int count, int size) {
        return LongStream.rangeClosed(1, count).mapToObj(seq -> turn(seq, "问".repeat(size), "答".repeat(size))).toList();
    }

    private static CompletedContextTurn turn(long seq, String user, String assistant) {
        return new CompletedContextTurn("t" + seq, user, assistant, LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(seq), seq);
    }
}
