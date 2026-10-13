package org.example.ai_agent.chat.assistant;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.ToIntFunction;

/** Shared estimate for history selection and every model request, including tool continuations. */
public final class AssistantInputBudget {
    private final int limit;
    private final ToIntFunction<String> estimate;

    public AssistantInputBudget(Integer limit, String estimatorModel) {
        this.limit = limit == null ? 8192 : Math.max(256, Math.min(131072, limit));
        if (estimatorModel == null || estimatorModel.isBlank()) {
            estimate = text -> text.getBytes(StandardCharsets.UTF_8).length;
        } else {
            var tokenizer = new OpenAiTokenCountEstimator(estimatorModel);
            estimate = tokenizer::estimateTokenCountInText;
        }
    }

    public int limit() {
        return limit;
    }

    public long estimateInput(List<ChatMessage> messages, List<String> toolDescriptions) {
        long total = 32;
        for (var message : messages) total += estimateMessage(message);
        for (var tool : toolDescriptions) total += estimateText(tool) + 16;
        return total;
    }

    public long estimateMessage(ChatMessage message) {
        long content = switch (message) {
            case SystemMessage system -> estimateText(system.text());
            case UserMessage user when user.hasSingleText() ->
                    estimateText(user.name()) + estimateText(user.singleText());
            case AiMessage ai -> {
                long tokens = estimateText(ai.text()) + estimateText(ai.thinking());
                if (ai.hasToolExecutionRequests()) {
                    for (var tool : ai.toolExecutionRequests()) {
                        tokens += estimateText(tool.id()) + estimateText(tool.name())
                                + estimateText(tool.arguments()) + 16;
                    }
                }
                yield tokens;
            }
            case ToolExecutionResultMessage result when result.hasSingleText() ->
                    estimateText(result.id()) + estimateText(result.toolName()) + estimateText(result.text());
            default -> estimateText(ChatMessageSerializer.messageToJson(message));
        };
        return content + 16;
    }

    private long estimateText(String text) {
        return text == null || text.isEmpty() ? 0 : estimate.applyAsInt(text);
    }
}
