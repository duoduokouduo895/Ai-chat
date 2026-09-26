package org.example.ai_agent.service.assistant;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.example.ai_agent.service.impl.chat.ChatStreamClientException;

import java.util.List;

/** Checks every provider request, including subsequent requests containing accumulated tool results. */
final class InputBudgetStreamingChatModel implements StreamingChatModel {
    private final StreamingChatModel delegate;
    private final AssistantInputBudget budget;

    InputBudgetStreamingChatModel(StreamingChatModel delegate, Integer budget, String estimatorModel) {
        this.delegate = delegate;
        this.budget = new AssistantInputBudget(budget, estimatorModel);
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        chat(request, handler);
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        var tools = request.toolSpecifications() == null ? List.<String>of()
                : request.toolSpecifications().stream().map(ToolSpecification::toJson).toList();
        if (budget.estimateInput(request.messages(), tools) > budget.limit()) {
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
