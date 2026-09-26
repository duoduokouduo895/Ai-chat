package org.example.ai_agent.service.memory;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.FinishReason;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.ai.ModelCatalogProperties;
import org.example.ai_agent.conversation.CompletedContextTurn;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.example.ai_agent.service.assistant.AssistantInputBudget;
import org.example.ai_agent.service.assistant.GeneralAssistant;
import org.example.ai_agent.service.assistant.ModelCompletions;
import org.example.ai_agent.service.impl.chat.ConversationContextLoader;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background rolling summarizer. It never runs on the request path: requests only read the
 * latest committed version, and a failed or slow compaction leaves the previous version in place.
 */
@Slf4j
@Component
public class ConversationMemoryCompactor {
    private static final int TURN_READ_LIMIT = 200;
    private static final int EVICTION_BATCH = 500;
    private static final int MAX_EVICTION_BATCHES = 20;

    private final ConversationMemoryStore store;
    private final MessageRepository messages;
    private final AiModelRegistry models;
    private final ModelCatalogProperties catalog;
    private final ConversationMemoryProperties properties;
    private final MeterRegistry meters;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong evictedBytes = new AtomicLong();

    public ConversationMemoryCompactor(ConversationMemoryStore store, MessageRepository messages,
                                       AiModelRegistry models, ModelCatalogProperties catalog,
                                       ConversationMemoryProperties properties, MeterRegistry meters) {
        this.store = store;
        this.messages = messages;
        this.models = models;
        this.catalog = catalog;
        this.properties = properties;
        this.meters = meters;
        meters.gauge("assistant.memory.archive.evicted.bytes", evictedBytes);
    }

    @Scheduled(fixedDelayString = "${assistant.memory.poll-delay-ms:3000}",
            initialDelayString = "${assistant.memory.initial-delay-ms:30000}")
    public void poll() {
        if (!properties.isEnabled() || !running.compareAndSet(false, true)) return;
        drain().doFinally(signal -> running.set(false))
                .subscribe(unused -> { }, error -> {
                    log.warn("会话记忆后台任务轮询失败: errorType={}", error.getClass().getSimpleName());
                    meters.counter("assistant.memory.poll.errors").increment();
                });
    }

    public Mono<Void> drain() {
        return evictArchive().then(store.due(2).concatMap(this::compact).then());
    }

    Mono<Void> evictArchive() {
        long cap = properties.getArchiveMaxSize().toBytes();
        return Flux.range(0, MAX_EVICTION_BATCHES)
                .concatMap(ignored -> store.evictOldestArchive(cap, EVICTION_BATCH))
                .takeUntil(freed -> freed == 0)
                .doOnNext(freed -> {
                    if (freed > 0) {
                        evictedBytes.addAndGet(freed);
                        log.info("会话归档超过容量上限，已淘汰最旧的归档轮次: freedBytes={}", freed);
                    }
                })
                .then();
    }

    Mono<Void> compact(Long conversationId) {
        Timer.Sample sample = Timer.start(meters);
        return store.claim(conversationId, properties.getModelTimeout().multipliedBy(2).plusMinutes(1))
                .flatMap(lease -> compact(lease)
                        .onErrorResume(error -> {
                            String code = error instanceof ConversationCompaction.InvalidCompactionException
                                    ? "MEMORY_INVALID_OUTPUT" : error instanceof java.util.concurrent.TimeoutException
                                    ? "MEMORY_MODEL_TIMEOUT" : error instanceof MemoryUnavailableException unavailable
                                    ? unavailable.code : "MEMORY_ERROR";
                            log.warn("会话记忆压缩失败: conversationId={}, attempt={}, errorCode={}, errorType={}",
                                    conversationId, lease.attempt(), code, error.getClass().getSimpleName());
                            return store.fail(lease, code, properties.getMaxAttempts(), properties.getRetryBackoff(),
                                    properties.getFailedCooldown()).thenReturn("failed");
                        }))
                .doOnNext(outcome -> {
                    meters.counter("assistant.memory.compaction", "outcome", outcome).increment();
                    sample.stop(meters.timer("assistant.memory.compaction.duration", "outcome", outcome));
                })
                .then();
    }

    private Mono<String> compact(ConversationMemoryStore.Lease lease) {
        var config = lease.modelId() == null ? null : catalog.getModels().get(lease.modelId());
        if (config == null) return Mono.error(new MemoryUnavailableException("MEMORY_MODEL_UNAVAILABLE"));
        var budget = new AssistantInputBudget(config.getInputBudgetTokens(), config.getTokenEstimatorModel());
        int summaryMax = properties.getSummaryMaxTokens();
        long chatSystem = budget.estimateMessage(SystemMessage.from(GeneralAssistant.SYSTEM_PROMPT));
        long historyBudget = (long) (budget.limit() * (1 - properties.getMemoryMaxRatio())) - chatSystem;
        long keepRecent = (long) (Math.max(0, historyBudget) * properties.getKeepRecentRatio());
        long memoryCost = budget.estimateMessage(UserMessage.from(
                ConversationCompaction.userPrompt(lease.memory(), List.of(), budget, 0)));
        long batchTokens = budget.limit() - ConversationCompaction.systemCost(budget, summaryMax) - memoryCost - 256;
        if (batchTokens < 256) return Mono.error(new MemoryUnavailableException("MEMORY_BUDGET_EXHAUSTED"));

        return loadTurns(lease.conversationId(), lease.memory().coveredThroughSeq()).flatMap(turns -> {
            var plan = ConversationCompaction.plan(turns, budget, keepRecent,
                    properties.getKeepRecentMinTurns(), batchTokens);
            if (plan.batch().isEmpty()) return store.release(lease).thenReturn("noop");
            long perTurn = Math.max(256, batchTokens / 2);
            var input = dev.langchain4j.model.chat.request.ChatRequest.builder()
                    .messages(List.of(SystemMessage.from(ConversationCompaction.systemPrompt(summaryMax)),
                            UserMessage.from(ConversationCompaction.userPrompt(lease.memory(), plan.batch(), budget, perTurn))))
                    .maxOutputTokens(summaryMax * 2 + 512)
                    .build();
            return Mono.defer(() -> ModelCompletions.complete(models.getStreamingModel(lease.modelId()), input,
                            budget.limit(), config.getTokenEstimatorModel()))
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(properties.getModelTimeout())
                    .map(response -> {
                        if (response.finishReason() != null && response.finishReason() != FinishReason.STOP) {
                            throw new ConversationCompaction.InvalidCompactionException("incomplete output");
                        }
                        return ConversationCompaction.parse(response.aiMessage().text(), plan.batch(), lease.memory(),
                                budget, summaryMax, properties.getMaxConstraints());
                    })
                    .flatMap(result -> store.commit(lease, result, plan.batch(),
                            plan.batch().getLast().sequenceId(), plan.morePending(),
                            properties.getArchiveMaxSize().toBytes() > 0, properties.getSummaryVersionsRetained()))
                    .map(committed -> {
                        if (committed) {
                            log.info("会话记忆已更新: conversationId={}, version={}, compactedTurns={}, morePending={}",
                                    lease.conversationId(), lease.memory().version() + 1, plan.batch().size(),
                                    plan.morePending());
                        }
                        return committed ? "success" : "conflict";
                    });
        });
    }

    /** Original turns after the watermark, oldest first, including legacy message pairs. */
    Mono<List<CompletedContextTurn>> loadTurns(Long conversationId, long afterSeq) {
        return Mono.zip(messages.findCompletedContextAfter(conversationId, afterSeq, TURN_READ_LIMIT).collectList(),
                        messages.findLegacyContext(conversationId).collectList())
                .map(rows -> {
                    List<CompletedContextTurn> turns = new ArrayList<>(rows.getT1());
                    ConversationContextLoader.legacyPairs(rows.getT2()).stream()
                            .filter(turn -> turn.sequenceId() != null && turn.sequenceId() > afterSeq)
                            .forEach(turns::add);
                    turns.sort(Comparator.comparing(CompletedContextTurn::sequenceId));
                    return List.copyOf(turns);
                });
    }

    static final class MemoryUnavailableException extends RuntimeException {
        final String code;

        MemoryUnavailableException(String code) {
            super(code);
            this.code = code;
        }
    }
}
