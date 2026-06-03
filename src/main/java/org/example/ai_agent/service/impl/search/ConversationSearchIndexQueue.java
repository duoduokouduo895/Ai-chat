package org.example.ai_agent.service.impl.search;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.service.SearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Set;

/**
 * 将 Elasticsearch 索引更新从会话写入主链中解耦。
 * 搜索索引是可重建的派生数据。相同会话的连续任务会合并，短暂背压或单次同步失败
 * 会保留在 pending map 中等待重投。
 */
@Slf4j
@Service
public class ConversationSearchIndexQueue {

    private final SearchService searchService;
    private final Sinks.Many<Long> tasks;
    private final ConcurrentMap<Long, Operation> pendingTasks = new ConcurrentHashMap<>();
    private final Set<Long> scheduledConversationIds = ConcurrentHashMap.newKeySet();
    private final Counter enqueuedCounter;
    private final Counter coalescedCounter;
    private final Counter deferredCounter;
    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Duration retryInterval;
    private Disposable worker;
    private Disposable retryWorker;

    public ConversationSearchIndexQueue(
            SearchService searchService,
            MeterRegistry meterRegistry,
            @Value("${search.index-sync.queue-capacity:500}") int queueCapacity,
            @Value("${search.index-sync.retry-interval-ms:1000}") long retryIntervalMs) {
        this.searchService = searchService;
        this.tasks = Sinks.many().unicast()
                .onBackpressureBuffer(new ArrayBlockingQueue<>(Math.max(1, queueCapacity)));
        this.retryInterval = Duration.ofMillis(Math.max(100, retryIntervalMs));
        this.enqueuedCounter = meterRegistry.counter("search.index.queue.enqueued");
        this.coalescedCounter = meterRegistry.counter("search.index.queue.coalesced");
        this.deferredCounter = meterRegistry.counter("search.index.queue.deferred");
        this.processedCounter = meterRegistry.counter("search.index.queue.processed");
        this.failedCounter = meterRegistry.counter("search.index.queue.failed");
        Gauge.builder("search.index.queue.pending", pendingTasks, ConcurrentMap::size)
                .register(meterRegistry);
    }

    @PostConstruct
    public void start() {
        worker = tasks.asFlux()
                .concatMap(this::execute)
                .subscribe(
                        ignored -> {
                        },
                        error -> log.error("会话搜索索引后台队列异常终止", error)
                );
        retryWorker = reactor.core.publisher.Flux.interval(retryInterval)
                .subscribe(ignored -> drainPendingTasks());
    }

    public void enqueueUpsert(Long conversationId) {
        mergeAndSchedule(conversationId, Operation.UPSERT);
    }

    public void enqueueDelete(Long conversationId) {
        mergeAndSchedule(conversationId, Operation.DELETE);
    }

    private Mono<Void> execute(Long conversationId) {
        scheduledConversationIds.remove(conversationId);
        Operation pendingOperation = pendingTasks.remove(conversationId);
        if (pendingOperation == null) {
            return Mono.empty();
        }

        Mono<Void> operation = pendingOperation == Operation.UPSERT
                ? searchService.indexConversation(conversationId)
                : searchService.deleteConversationIndex(conversationId);

        return operation
            .doOnSuccess(ignored -> processedCounter.increment())
            .onErrorResume(error -> {
            failedCounter.increment();
            pendingTasks.merge(conversationId, pendingOperation, this::mergeOperations);
            log.error("会话搜索索引后台同步失败: conversationId={}, operation={}",
                    conversationId, pendingOperation, error);
            return Mono.empty();
        });
    }

    private void mergeAndSchedule(Long conversationId, Operation operation) {
        if (conversationId == null || conversationId <= 0) {
            log.warn("忽略无效的会话索引任务: conversationId={}, operation={}", conversationId, operation);
            return;
        }

        pendingTasks.compute(conversationId, (ignored, current) -> {
            if (current != null) {
                coalescedCounter.increment();
            }
            return mergeOperations(current, operation);
        });
        schedule(conversationId);
    }

    private Operation mergeOperations(Operation current, Operation incoming) {
        return current == Operation.DELETE || incoming == Operation.DELETE
                ? Operation.DELETE
                : Operation.UPSERT;
    }

    private void schedule(Long conversationId) {
        if (!pendingTasks.containsKey(conversationId) || !scheduledConversationIds.add(conversationId)) {
            return;
        }

        Sinks.EmitResult result = tasks.tryEmitNext(conversationId);
        if (result.isFailure()) {
            scheduledConversationIds.remove(conversationId);
            deferredCounter.increment();
            log.warn("会话搜索索引后台队列暂时不可用，任务保留等待重投: conversationId={}, result={}",
                    conversationId, result);
            return;
        }
        enqueuedCounter.increment();
    }

    private void drainPendingTasks() {
        pendingTasks.keySet().forEach(this::schedule);
    }

    @PreDestroy
    public void stop() {
        tasks.tryEmitComplete();
        if (retryWorker != null) {
            retryWorker.dispose();
        }
        if (worker != null) {
            worker.dispose();
        }
    }

    private enum Operation {
        UPSERT,
        DELETE
    }
}
