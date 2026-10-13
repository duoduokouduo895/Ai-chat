package org.example.ai_agent.chat.service;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import io.micrometer.core.instrument.Metrics;
import org.example.ai_agent.llm.ModelCatalogProperties;
import org.example.ai_agent.chat.domain.CompletedContextTurn;
import org.example.ai_agent.chat.dto.ChatRequest;
import org.example.ai_agent.chat.entity.Message;
import org.example.ai_agent.chat.repository.MessageRepository;
import org.example.ai_agent.chat.assistant.AssistantInputBudget;
import org.example.ai_agent.chat.memory.ArchivedTurn;
import org.example.ai_agent.chat.memory.ConversationHistory;
import org.example.ai_agent.chat.memory.ConversationMemoryBlock;
import org.example.ai_agent.chat.memory.ConversationMemoryService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class ConversationContextLoader implements org.example.ai_agent.chat.domain.ConversationContext {
    private final MessageRepository messages;
    private final ModelCatalogProperties models;
    private final ConversationMemoryService memory;

    public ConversationContextLoader(MessageRepository messages, ModelCatalogProperties models,
                                     ConversationMemoryService memory) {
        this.messages = messages;
        this.models = models;
        this.memory = memory;
    }

    public Mono<String> prepare(ChatRequest request, String input) {
        return load(request.getConversationId()).flatMap(history -> prepare(request, input, history));
    }

    Mono<String> prepare(ChatRequest request, String input, ConversationHistory history) {
        String question = request.getTrimmedMessage();
        boolean recall = history.memory().hasArchive() && ConversationMemoryBlock.refersToEarlierConversation(question);
        String query = request.getRetrievalContext() != null && request.getRetrievalContext().question() != null
                ? request.getRetrievalContext().question() : question;
        Mono<List<ArchivedTurn>> recalled = recall
                ? memory.recall(request.getConversationId(), query, memory.properties().getRecallMaxResults(), "auto")
                : Mono.just(List.of());
        return recalled.map(hits -> prepareInput(request, input, history, hits));
    }

    private String prepareInput(ChatRequest request, String input, ConversationHistory history,
                                List<ArchivedTurn> recalled) {
        var config = models.getModels().get(request.getEffectiveModelId());
        request.setResolvedInputBudgetTokens(config == null ? null : config.getInputBudgetTokens());
        request.setResolvedTokenEstimatorModel(config == null ? null : config.getTokenEstimatorModel());
        List<String> candidates = new ArrayList<>(List.of(input));
        List<org.example.ai_agent.knowledge.service.RagService.RetrievalContext> contexts = new ArrayList<>();
        if (request.getRetrievalContext() != null) {
            var context = request.getRetrievalContext();
            contexts.add(context);
            var evidence = new ArrayList<>(context.relevantDocuments());
            while (evidence.size() > 1) {
                evidence.removeLast();
                String text = evidence.stream().map(match -> "文档：" + match.getDocumentTitle()
                        + "\n内容：" + match.getContent()).collect(java.util.stream.Collectors.joining("\n---\n"));
                context = new org.example.ai_agent.knowledge.service.RagService.RetrievalContext(context.question(),
                        List.copyOf(evidence), text, evidence.size(), context.retrievalTime(),
                        context.status(), context.channels());
                contexts.add(context);
                candidates.add(AssistantInputBuilder.buildKnowledgeAugmentedPrompt(request, context));
            }
        }
        request.setHistoryRecallAvailable(!request.isRagMode() && memory.properties().isEnabled()
                && history.memory().hasArchive());
        var command = new InputRequest(request.getConversationId(), request.getEffectiveModelId(), candidates,
                org.example.ai_agent.chat.assistant.GeneralAssistant.SYSTEM_PROMPT, assistantTools(request));
        var selection = select(command, history, config, recalled);
        var prepared = selection.prepared();
        request.setContextTurns(prepared.turns());
        request.setEstimatedInputTokens(prepared.estimatedInputTokens());
        request.setMemoryBlock(selection.memoryBlock());
        request.setMemoryCompactionDue(selection.compactionDue());
        if (!contexts.isEmpty()) request.setRetrievalContext(contexts.get(prepared.candidateIndex()));
        return prepared.input();
    }

    /** Descriptions of exactly the tools GeneralAssistantFactory will expose for this request. */
    static List<String> assistantTools(ChatRequest request) {
        if (request.isRagMode()) return List.of();
        List<String> tools = new ArrayList<>(dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom(
                        org.example.ai_agent.chat.assistant.KnowledgeAssistantTools.class)
                .stream().map(ToolSpecification::toJson).toList());
        if (request.isHistoryRecallAvailable()) {
            dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom(
                            org.example.ai_agent.chat.assistant.ConversationHistoryTools.class)
                    .stream().map(ToolSpecification::toJson).forEach(tools::add);
        }
        return List.copyOf(tools);
    }

    @Override
    public java.util.concurrent.CompletionStage<PreparedInput> prepare(InputRequest request) {
        return load(request.conversationId())
                .map(history -> select(request, history, models.getModels().get(request.modelId()), List.of()).prepared())
                .toFuture();
    }

    /** Reads memory before the originals, so every turn after the watermark is guaranteed to be present. */
    public Mono<ConversationHistory> load(Long conversationId) {
        return memory.load(conversationId).flatMap(state -> loadTurns(conversationId)
                .map(turns -> new ConversationHistory(state, turns.stream()
                        .filter(turn -> !state.hasArchive() || turn.sequenceId() == null
                                || turn.sequenceId() > state.coveredThroughSeq())
                        .toList())));
    }

    private Mono<List<CompletedContextTurn>> loadTurns(Long conversationId) {
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

    private Selection select(InputRequest request, ConversationHistory conversation,
                             ModelCatalogProperties.ModelConfig config, List<ArchivedTurn> recalled) {
        List<CompletedContextTurn> history = conversation.turns();
        var budget = new AssistantInputBudget(config == null ? null : config.getInputBudgetTokens(),
                config == null ? null : config.getTokenEstimatorModel());
        long overhead = budget.estimateInput(List.of(SystemMessage.from(request.systemPrompt())),
                request.toolDescriptions());
        if (!request.toolDescriptions().isEmpty()) {
            int reserve = config == null || config.getToolOutputReserveTokens() == null
                    ? 1024 : config.getToolOutputReserveTokens();
            overhead += Math.max(128, Math.min(budget.limit() / 2, reserve));
        }
        var settings = memory.properties();
        String block = settings.isEnabled() ? ConversationMemoryBlock.render(conversation.memory(), recalled, budget,
                (long) (budget.limit() * settings.getMemoryMaxRatio()), settings.getRecallMaxChars()) : null;
        int candidate = firstFitting(request, budget, overhead + ConversationMemoryBlock.cost(budget, block));
        if (candidate < 0 && block != null) {
            // The question and its evidence outrank background memory.
            block = null;
            candidate = firstFitting(request, budget, overhead);
        }
        if (candidate < 0) throw ChatStreamClientException.inputTooLarge();
        String input = request.inputCandidates().get(candidate);
        long inputCost = budget.estimateMessage(UserMessage.from(input));
        long fixed = inputCost + overhead + ConversationMemoryBlock.cost(budget, block);

        // Select a contiguous suffix of whole turns, including space recovered by reducing evidence.
        long total = fixed;
        long unsummarized = 0;
        int first = history.size();
        for (int index = history.size() - 1; index >= 0; index--) {
            var turn = history.get(index);
            long cost = budget.estimateMessage(UserMessage.from(turn.userText()))
                    + budget.estimateMessage(AiMessage.from(turn.assistantText()));
            unsummarized += cost;
            if (first == index + 1 && total + cost <= budget.limit()) {
                total += cost;
                first = index;
            }
        }
        // Turns after the watermark that did not fit are neither summarized nor sent.
        boolean gap = first > 0;
        if (gap && settings.isEnabled()) Metrics.counter("assistant.memory.context.gap").increment();
        long historyBudget = Math.max(0, budget.limit() - fixed);
        boolean due = settings.isEnabled() && request.conversationId() != null
                && (gap || unsummarized + inputCost > settings.getTriggerRatio() * historyBudget);
        return new Selection(new PreparedInput(input, candidate, history.subList(first, history.size()), (int) total),
                block, due);
    }

    private static int firstFitting(InputRequest request, AssistantInputBudget budget, long overhead) {
        for (int candidate = 0; candidate < request.inputCandidates().size(); candidate++) {
            long fixed = budget.estimateMessage(UserMessage.from(request.inputCandidates().get(candidate))) + overhead;
            if (fixed <= budget.limit()) return candidate;
        }
        return -1;
    }

    private record Selection(PreparedInput prepared, String memoryBlock, boolean compactionDue) {
    }

    public static List<CompletedContextTurn> legacyPairs(List<Message> descending) {
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
