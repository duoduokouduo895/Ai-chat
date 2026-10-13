package org.example.ai_agent.chat.memory;

import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.chat.dto.ChatRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Read side of long-conversation memory. Memory is an optimisation over the originals, so any
 * failure here degrades to the previous newest-turns-only behaviour instead of failing the chat.
 */
@Slf4j
@Service
public class ConversationMemoryService {
    private static final int MAX_QUERY_CHARS = 200;

    private final ConversationMemoryStore store;
    private final ConversationMemoryProperties properties;

    public ConversationMemoryService(ConversationMemoryStore store, ConversationMemoryProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    public ConversationMemoryProperties properties() {
        return properties;
    }

    public Mono<ConversationMemory> load(Long conversationId) {
        if (!properties.isEnabled() || conversationId == null) return Mono.just(ConversationMemory.EMPTY);
        return store.load(conversationId)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(error -> {
                    log.warn("会话记忆读取失败，退回仅使用最近轮次: conversationId={}, errorType={}",
                            conversationId, error.getClass().getSimpleName());
                    Metrics.counter("assistant.memory.load.errors").increment();
                    return Mono.just(ConversationMemory.EMPTY);
                });
    }

    public Mono<List<ArchivedTurn>> recall(Long conversationId, String query, int limit, String source) {
        String normalized = query == null ? "" : query.strip();
        if (!properties.isEnabled() || conversationId == null || normalized.isEmpty() || limit <= 0) {
            return Mono.just(List.of());
        }
        if (normalized.length() > MAX_QUERY_CHARS) normalized = normalized.substring(0, MAX_QUERY_CHARS);
        return store.search(conversationId, normalized, limit).collectList()
                .timeout(Duration.ofSeconds(3))
                .doOnNext(hits -> Metrics.counter("assistant.memory.recall", "source", source,
                        "outcome", hits.isEmpty() ? "miss" : "hit").increment())
                .onErrorResume(error -> {
                    log.warn("会话历史检索失败: conversationId={}, errorType={}",
                            conversationId, error.getClass().getSimpleName());
                    Metrics.counter("assistant.memory.recall", "source", source, "outcome", "error").increment();
                    return Mono.just(List.of());
                });
    }

    /** Best effort: a missed trigger is re-evaluated on the next request of the conversation. */
    public void afterTurnCompleted(ChatRequest request) {
        if (!properties.isEnabled() || !request.isMemoryCompactionDue() || request.getConversationId() == null
                || request.getEffectiveModelId() == null) return;
        store.requestCompaction(request.getConversationId(), request.getEffectiveModelId())
                .subscribe(ignored -> { }, error -> log.warn("会话记忆压缩登记失败: conversationId={}, errorType={}",
                        request.getConversationId(), error.getClass().getSimpleName()));
    }
}
