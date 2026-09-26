package org.example.ai_agent.service.impl.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.example.ai_agent.config.ai.ModelCatalogProperties;
import org.example.ai_agent.conversation.CompletedContextTurn;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.example.ai_agent.service.assistant.AssistantInputBudget;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class ConversationContextLoader implements org.example.ai_agent.conversation.ConversationContext {
    private final MessageRepository messages;
    private final ModelCatalogProperties models;

    public ConversationContextLoader(MessageRepository messages, ModelCatalogProperties models) {
        this.messages = messages;
        this.models = models;
    }

    public Mono<String> prepare(ChatRequest request, String input) {
        return load(request.getConversationId()).flatMap(turns -> prepare(request, input, turns));
    }

    Mono<String> prepare(ChatRequest request, String input, List<CompletedContextTurn> history) {
        return Mono.fromSupplier(() -> prepareInput(request, input, history));
    }

    private String prepareInput(ChatRequest request, String input, List<CompletedContextTurn> history) {
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
                        .stream().map(ToolSpecification::toJson).toList();
        var command = new InputRequest(request.getConversationId(), request.getEffectiveModelId(), candidates,
                org.example.ai_agent.service.assistant.GeneralAssistant.SYSTEM_PROMPT, tools);
        var prepared = select(command, history, config);
        request.setContextTurns(prepared.turns());
        request.setEstimatedInputTokens(prepared.estimatedInputTokens());
        if (!contexts.isEmpty()) request.setRetrievalContext(contexts.get(prepared.candidateIndex()));
        return prepared.input();
    }

    @Override
    public java.util.concurrent.CompletionStage<PreparedInput> prepare(InputRequest request) {
        return load(request.conversationId())
                .map(turns -> select(request, turns, models.getModels().get(request.modelId())))
                .toFuture();
    }

    Mono<List<CompletedContextTurn>> load(Long conversationId) {
        return Mono.zip(messages.findCompletedContext(conversationId).collectList(),
                        messages.findLegacyContext(conversationId).collectList())
                .map(rows -> {
                    List<CompletedContextTurn> turns = new ArrayList<>(rows.getT1());
                    turns.addAll(legacyPairs(rows.getT2()));
                    turns.sort(Comparator.comparing(CompletedContextTurn::completedAt)
                            .thenComparing(CompletedContextTurn::sequenceId, Comparator.nullsFirst(Long::compareTo))
                            .thenComparing(CompletedContextTurn::turnId));
                    return List.copyOf(turns);
                });
    }

    private PreparedInput select(InputRequest request, List<CompletedContextTurn> history,
                                 ModelCatalogProperties.ModelConfig config) {
        var budget = new AssistantInputBudget(config == null ? null : config.getInputBudgetTokens(),
                config == null ? null : config.getTokenEstimatorModel());
        long overhead = budget.estimateInput(List.of(SystemMessage.from(request.systemPrompt())),
                request.toolDescriptions());
        if (!request.toolDescriptions().isEmpty()) {
            int reserve = config == null || config.getToolOutputReserveTokens() == null
                    ? 1024 : config.getToolOutputReserveTokens();
            overhead += Math.max(128, Math.min(budget.limit() / 2, reserve));
        }
        int candidate = 0;
        long fixed = budget.estimateMessage(UserMessage.from(request.inputCandidates().get(candidate))) + overhead;
        while (fixed > budget.limit() && candidate + 1 < request.inputCandidates().size()) {
            candidate++;
            fixed = budget.estimateMessage(UserMessage.from(request.inputCandidates().get(candidate))) + overhead;
        }
        if (fixed > budget.limit()) throw ChatStreamClientException.inputTooLarge();

        // Select a contiguous suffix of whole turns, including space recovered by reducing evidence.
        long total = fixed;
        int first = history.size();
        while (first > 0) {
            var turn = history.get(first - 1);
            long cost = budget.estimateMessage(UserMessage.from(turn.userText()))
                    + budget.estimateMessage(AiMessage.from(turn.assistantText()));
            if (total + cost > budget.limit()) break;
            total += cost;
            first--;
        }
        return new PreparedInput(request.inputCandidates().get(candidate), candidate,
                history.subList(first, history.size()), (int) total);
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
