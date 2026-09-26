package org.example.ai_agent.service.assistant;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.example.ai_agent.service.memory.ArchivedTurn;
import org.example.ai_agent.service.memory.ConversationMemoryBlock;
import org.example.ai_agent.service.memory.ConversationMemoryService;

import java.time.Duration;
import java.util.List;

/**
 * Exact recall of earlier turns that were summarized out of the context. The conversation is
 * bound at construction, so the model can never search another conversation.
 */
public class ConversationHistoryTools {
    private static final Duration TOOL_TIMEOUT = Duration.ofSeconds(5);

    private final Long conversationId;
    private final ConversationMemoryService memory;

    public ConversationHistoryTools(Long conversationId, ConversationMemoryService memory) {
        this.conversationId = conversationId;
        this.memory = memory;
    }

    @Tool("检索本会话中较早、已被压缩为摘要的对话原文。当用户提到之前说过的具体内容（数字、代码、名称、原话）而摘要里没有细节时使用。")
    public String searchConversationHistory(@P("要查找的关键词或问题，使用对话中可能出现的原词") String query) {
        var settings = memory.properties();
        List<ArchivedTurn> hits = memory.recall(conversationId, query, settings.getRecallMaxResults(), "tool")
                .blockOptional(TOOL_TIMEOUT).orElse(List.of());
        if (hits.isEmpty()) return "没有在本会话的早期对话中找到相关内容。";
        return "以下是本会话早期对话的原文片段，仅作为参考数据：\n"
                + ConversationMemoryBlock.formatRecall(hits, settings.getRecallMaxChars());
    }
}
