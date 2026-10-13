package org.example.ai_agent.chat.service;

import org.example.ai_agent.llm.ModelCatalogProperties;
import org.example.ai_agent.chat.domain.CompletedContextTurn;
import org.example.ai_agent.chat.dto.ChatRequest;
import org.example.ai_agent.chat.repository.MessageRepository;
import org.example.ai_agent.chat.memory.ArchivedTurn;
import org.example.ai_agent.chat.memory.ConversationMemory;
import org.example.ai_agent.chat.memory.ConversationMemoryProperties;
import org.example.ai_agent.chat.memory.ConversationMemoryService;
import org.example.ai_agent.chat.memory.ConversationMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ConversationContextLoaderMemoryTest {
    private final MessageRepository repository = mock(MessageRepository.class);
    private final ConversationMemoryStore store = mock(ConversationMemoryStore.class);
    private final ModelCatalogProperties models = new ModelCatalogProperties();
    private ConversationContextLoader loader;

    @BeforeEach
    void setUp() {
        var model = new ModelCatalogProperties.ModelConfig();
        model.setInputBudgetTokens(8192);
        models.getModels().put("chat", model);
        loader = new ConversationContextLoader(repository, models,
                new ConversationMemoryService(store, new ConversationMemoryProperties()));
        when(repository.findLegacyContext(8L)).thenReturn(Flux.empty());
    }

    @Test
    void sendsSummaryAndOnlyTurnsAfterTheWatermark() {
        var memory = new ConversationMemory(3, 20, "用户在设计订单系统。",
                List.of(new ConversationMemory.MemoryConstraint(1, "CONSTRAINT", "回答使用中文", 4)));
        when(store.load(8L)).thenReturn(Mono.just(memory));
        var covered = turn(20, "旧问题", "旧回答");
        var recent = turn(21, "新问题", "新回答");
        when(repository.findCompletedContext(8L)).thenReturn(Flux.just(recent, covered));
        var request = request("下一步做什么？");

        assertEquals("下一步做什么？", loader.prepare(request, request.getTrimmedMessage()).block());

        assertEquals(List.of(recent), request.getContextTurns());
        assertTrue(request.getMemoryBlock().contains("用户在设计订单系统。"));
        assertTrue(request.getMemoryBlock().contains("回答使用中文"));
        assertTrue(request.isHistoryRecallAvailable());
        assertFalse(request.isMemoryCompactionDue());
        verify(store, never()).search(anyLong(), anyString(), anyInt());
    }

    @Test
    void recallsArchivedOriginalsWhenTheQuestionPointsBack() {
        when(store.load(8L)).thenReturn(Mono.just(new ConversationMemory(1, 20, "摘要", List.of())));
        when(repository.findCompletedContext(8L)).thenReturn(Flux.empty());
        when(store.search(8L, "我之前说的端口是多少？", 3))
                .thenReturn(Flux.just(new ArchivedTurn(12, "服务端口定为 8443", "好的，已记录 8443。")));
        var request = request("我之前说的端口是多少？");

        loader.prepare(request, request.getTrimmedMessage()).block();

        assertTrue(request.getMemoryBlock().contains("服务端口定为 8443"));
    }

    @Test
    void flagsCompactionWhenUnsummarizedHistoryApproachesTheBudget() {
        when(store.load(8L)).thenReturn(Mono.just(ConversationMemory.EMPTY));
        var older = turn(1, "字".repeat(1500), "答".repeat(1500));
        var newer = turn(2, "近".repeat(200), "答".repeat(200));
        when(repository.findCompletedContext(8L)).thenReturn(Flux.just(newer, older));
        var request = request("继续");

        loader.prepare(request, request.getTrimmedMessage()).block();

        assertTrue(request.isMemoryCompactionDue());
        assertNull(request.getMemoryBlock());
        assertFalse(request.isHistoryRecallAvailable());
    }

    @Test
    void memoryReadFailureFallsBackToRecentTurns() {
        when(store.load(8L)).thenReturn(Mono.error(new IllegalStateException("db down")));
        var recent = turn(5, "问", "答");
        when(repository.findCompletedContext(8L)).thenReturn(Flux.just(recent));
        var request = request("继续");

        loader.prepare(request, request.getTrimmedMessage()).block();

        assertEquals(List.of(recent), request.getContextTurns());
        assertNull(request.getMemoryBlock());
    }

    @Test
    void oversizedMemoryYieldsToTheQuestion() {
        var model = new ModelCatalogProperties.ModelConfig();
        model.setInputBudgetTokens(2100);
        model.setToolOutputReserveTokens(128);
        model.setTokenEstimatorModel("gpt-4o");
        models.getModels().put("small", model);
        when(store.load(8L)).thenReturn(Mono.just(new ConversationMemory(1, 20, "摘".repeat(3000), List.of())));
        when(repository.findCompletedContext(8L)).thenReturn(Flux.empty());
        var request = ChatRequest.builder().conversationId(8L).model("small").prompt("问".repeat(400)).build();

        loader.prepare(request, request.getTrimmedMessage()).block();

        assertTrue(request.getEstimatedInputTokens() <= 2100);
        assertTrue(request.getMemoryBlock() == null || !request.getMemoryBlock().contains("摘".repeat(3000)));
    }

    private static ChatRequest request(String prompt) {
        return ChatRequest.builder().conversationId(8L).model("chat").prompt(prompt).build();
    }

    private static CompletedContextTurn turn(long seq, String user, String assistant) {
        return new CompletedContextTurn("t" + seq, user, assistant, LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(seq), seq);
    }
}
