package org.example.ai_agent.service.assistant;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.example.ai_agent.service.impl.chat.ChatStreamClientException;

import java.nio.charset.StandardCharsets;
import java.util.function.ToIntFunction;

/** Checks every provider request, including subsequent requests containing accumulated tool results. */
final class InputBudgetStreamingChatModel implements StreamingChatModel {
    private final StreamingChatModel delegate;
    private final int budget;
    private final ToIntFunction<String> estimate;

    InputBudgetStreamingChatModel(StreamingChatModel delegate, Integer budget, String estimatorModel) {
        this.delegate = delegate;
        this.budget = budget == null ? 8192 : Math.max(256, Math.min(131072, budget));
        if (estimatorModel == null || estimatorModel.isBlank()) {
            estimate = text -> text.getBytes(StandardCharsets.UTF_8).length;
        } else {
            var estimator = new dev.langchain4j.model.openai.OpenAiTokenCountEstimator(estimatorModel);
            estimate = estimator::estimateTokenCountInText;
        }
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        chat(request, handler);
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        long tokens = 32;
        for (var message : request.messages()) tokens += estimate.applyAsInt(message.toString()) + 16L;
        if (request.toolSpecifications() != null) {
            for (var tool : request.toolSpecifications()) tokens += estimate.applyAsInt(tool.toString()) + 16L;
        }
        if (tokens > budget) {
            handler.onError(ChatStreamClientException.inputTooLarge());
            return;
        }
        delegate.chat(request, handler);
    }

    @Override
    public java.util.Set<dev.langchain4j.model.chat.Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    @Override
    public dev.langchain4j.model.chat.request.ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public dev.langchain4j.model.ModelProvider provider() { return delegate.provider(); }
}
