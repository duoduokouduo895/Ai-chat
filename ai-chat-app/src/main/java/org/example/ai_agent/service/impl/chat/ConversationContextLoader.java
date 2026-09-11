package org.example.ai_agent.service.impl.chat;

import org.example.ai_agent.config.ai.ModelCatalogProperties;
import org.example.ai_agent.conversation.CompletedContextTurn;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

@Component
public class ConversationContextLoader implements org.example.ai_agent.conversation.ConversationContext {
    private final MessageRepository messages;
    private final ModelCatalogProperties models;

    public ConversationContextLoader(MessageRepository messages, ModelCatalogProperties models) {
        this.messages = messages;
        this.models = models;
    }

    public Mono<String> prepare(ChatRequest request, String input) {
        var config = models.getModels().get(request.getEffectiveModelId());
        request.setResolvedInputBudgetTokens(config == null ? null : config.getInputBudgetTokens());
        request.setResolvedTokenEstimatorModel(config == null ? null : config.getTokenEstimatorModel());
        List<String> candidates = new ArrayList<>(List.of(input));
        List<org.example.ai_agent.service.RagService.RetrievalContext> contexts = new ArrayList<>();
        if (request.getRetrievalContext() != null) {
            var context = request.getRetrievalContext();
            contexts.add(context);
            var evidence = new ArrayList<>(context.relevantDocuments());
            while (evidence.size() > 1) {
                evidence.removeLast();
                String text = evidence.stream().map(match -> "文档：" + match.getDocumentTitle()
                        + "\n内容：" + match.getContent()).collect(java.util.stream.Collectors.joining("\n---\n"));
                context = new org.example.ai_agent.service.RagService.RetrievalContext(context.question(),
                        List.copyOf(evidence), text, evidence.size(), context.retrievalTime(),
                        context.status(), context.channels());
                contexts.add(context);
                candidates.add(AssistantInputBuilder.buildKnowledgeAugmentedPrompt(request, context));
            }
        }
        List<String> tools = request.isRagMode() ? List.of()
                : dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom(
                        org.example.ai_agent.service.assistant.KnowledgeAssistantTools.class)
                        .stream().map(Object::toString).toList();
        var command = new InputRequest(request.getConversationId(), request.getEffectiveModelId(), candidates,
                org.example.ai_agent.service.assistant.GeneralAssistant.SYSTEM_PROMPT, tools);
        return Mono.fromCompletionStage(() -> prepare(command)).map(prepared -> {
            request.setContextTurns(prepared.turns());
            request.setEstimatedInputTokens(prepared.estimatedInputTokens());
            if (!contexts.isEmpty()) request.setRetrievalContext(contexts.get(prepared.candidateIndex()));
            return prepared.input();
        });
    }

    @Override
    public java.util.concurrent.CompletionStage<PreparedInput> prepare(InputRequest request) {
        return Mono.zip(messages.findCompletedContext(request.conversationId()).collectList(),
                        messages.findLegacyContext(request.conversationId()).collectList())
                .map(rows -> {
                    List<CompletedContextTurn> turns = new ArrayList<>(rows.getT1());
                    turns.addAll(legacyPairs(rows.getT2()));
                    turns.sort(Comparator.comparing(CompletedContextTurn::completedAt)
                            .thenComparing(CompletedContextTurn::sequenceId, Comparator.nullsFirst(Long::compareTo))
                            .thenComparing(CompletedContextTurn::turnId));
                    var config = models.getModels().get(request.modelId());
                    int budget = config == null || config.getInputBudgetTokens() == null ? 8192
                            : Math.max(256, Math.min(131072, config.getInputBudgetTokens()));
                    ToIntFunction<String> estimate = estimator(config);
                    int overhead = estimate.applyAsInt(request.systemPrompt()) + 32;
                    if (!request.toolDescriptions().isEmpty()) {
                        for (String tool : request.toolDescriptions()) overhead += estimate.applyAsInt(tool) + 16;
                        int reserve = config == null || config.getToolOutputReserveTokens() == null
                                ? 1024 : config.getToolOutputReserveTokens();
                        overhead += Math.max(128, Math.min(budget / 2, reserve));
                    }
                    int candidate = 0;
                    int fixed = estimate.applyAsInt(request.inputCandidates().get(candidate)) + overhead;
                    while (fixed > budget && candidate + 1 < request.inputCandidates().size()) {
                        turns.clear();
                        candidate++;
                        fixed = estimate.applyAsInt(request.inputCandidates().get(candidate)) + overhead;
                    }
                    if (fixed > budget) throw ChatStreamClientException.inputTooLarge();
                    int total = fixed;
                    for (var turn : turns) total += estimate.applyAsInt(turn.userText())
                            + estimate.applyAsInt(turn.assistantText()) + 16;
                    while (total > budget && !turns.isEmpty()) {
                        var oldest = turns.removeFirst();
                        total -= estimate.applyAsInt(oldest.userText()) + estimate.applyAsInt(oldest.assistantText()) + 16;
                    }
                    return new PreparedInput(request.inputCandidates().get(candidate), candidate, turns, total);
                }).toFuture();
    }

    private ToIntFunction<String> estimator(ModelCatalogProperties.ModelConfig config) {
        if (config != null && config.getTokenEstimatorModel() != null && !config.getTokenEstimatorModel().isBlank()) {
            var estimator = new dev.langchain4j.model.openai.OpenAiTokenCountEstimator(config.getTokenEstimatorModel());
            return estimator::estimateTokenCountInText;
        }
        return text -> text.getBytes(StandardCharsets.UTF_8).length;
    }

    static List<CompletedContextTurn> legacyPairs(List<Message> descending) {
        List<Message> ordered = new ArrayList<>(descending);
        ordered.sort(Comparator.comparing(Message::getTimestamp).thenComparing(Message::getId));
        List<CompletedContextTurn> pairs = new ArrayList<>();
        Message user = null;
        for (Message message : ordered) {
            if ("user".equals(message.getSender())) {
                user = message;
            } else {
                if ("ai".equals(message.getSender()) && user != null && Objects.equals(user.getModel(), message.getModel())) {
                    pairs.add(new CompletedContextTurn("legacy-" + message.getId(), user.getContent(),
                            message.getContent(), message.getTimestamp(), message.getId()));
                }
                user = null;
            }
        }
        return pairs;
    }
}
