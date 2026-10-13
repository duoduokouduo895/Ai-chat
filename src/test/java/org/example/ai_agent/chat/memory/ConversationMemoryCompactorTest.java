package org.example.ai_agent.chat.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.ai_agent.llm.ModelCatalogProperties;
import org.example.ai_agent.chat.domain.CompletedContextTurn;
import org.example.ai_agent.chat.repository.MessageRepository;
import org.example.ai_agent.llm.AiModelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationMemoryCompactorTest {
    private final ConversationMemoryStore store = mock(ConversationMemoryStore.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final AiModelRegistry registry = mock(AiModelRegistry.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AtomicReference<String> reply = new AtomicReference<>();
    private final AtomicReference<ChatRequest> sent = new AtomicReference<>();
    private final ConversationMemoryStore.Lease lease = new ConversationMemoryStore.Lease(8L, "token", "chat", 1,
            new ConversationMemory(2, 10, "旧摘要", List.of()));
    private ConversationMemoryCompactor compactor;

    @BeforeEach
    void setUp() {
        var catalog = new ModelCatalogProperties();
        var model = new ModelCatalogProperties.ModelConfig();
        model.setInputBudgetTokens(8192);
        catalog.getModels().put("chat", model);
        compactor = new ConversationMemoryCompactor(store, messages, registry, catalog,
                new ConversationMemoryProperties(), meters);
        when(store.claim(eq(8L), any())).thenReturn(Mono.just(lease));
        when(store.fail(any(), anyString(), anyInt(), any(), any())).thenReturn(Mono.empty());
        when(store.release(any())).thenReturn(Mono.empty());
        when(messages.findLegacyContext(8L)).thenReturn(Flux.empty());
        when(registry.getStreamingModel("chat")).thenReturn(new StreamingChatModel() {
            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                sent.set(request);
                handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(reply.get()))
                        .finishReason(FinishReason.STOP).build());
            }
        });
    }

    @Test
    void summarizesOldestTurnsAndAdvancesTheWatermark() {
        var turns = turns(11, 16);
        when(messages.findCompletedContextAfter(8L, 10L, 200)).thenReturn(Flux.fromIterable(turns));
        reply.set("""
                {"summary":"合并后的摘要","add_constraints":[
                  {"kind":"CONSTRAINT","content":"使用中文","source_seq":11,"quote":"问题11"}],
                 "remove_constraint_ids":[]}
                """);
        when(store.commit(any(), any(), any(), anyLong(), anyBoolean(), anyBoolean(), anyInt())).thenReturn(Mono.just(true));

        compactor.compact(8L).block();

        var result = ArgumentCaptor.forClass(CompactionResult.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CompletedContextTurn>> batch = ArgumentCaptor.forClass(List.class);
        var covered = ArgumentCaptor.forClass(Long.class);
        verify(store).commit(eq(lease), result.capture(), batch.capture(), covered.capture(),
                anyBoolean(), eq(true), eq(5));
        assertEquals("合并后的摘要", result.getValue().summary());
        assertEquals(1, result.getValue().addConstraints().size());
        assertEquals(11L, batch.getValue().getFirst().sequenceId());
        assertEquals(batch.getValue().getLast().sequenceId(), covered.getValue());
        assertTrue(covered.getValue() <= 14L, "at least the two newest turns stay original");
        assertTrue(sent.get().messages().getLast().toString().contains("旧摘要"));
        assertEquals(1.0, meters.counter("assistant.memory.compaction", "outcome", "success").count());
    }

    @Test
    void invalidModelOutputKeepsThePreviousVersionAndSchedulesRetry() {
        when(messages.findCompletedContextAfter(8L, 10L, 200)).thenReturn(Flux.fromIterable(turns(11, 16)));
        reply.set("抱歉，我无法完成。");

        compactor.compact(8L).block();

        verify(store).fail(eq(lease), eq("MEMORY_INVALID_OUTPUT"), eq(5), any(), any());
        verify(store, never()).commit(any(), any(), any(), anyLong(), anyBoolean(), anyBoolean(), anyInt());
    }

    @Test
    void releasesWithoutModelCallWhenOnlyRecentTurnsRemain() {
        when(messages.findCompletedContextAfter(8L, 10L, 200)).thenReturn(Flux.fromIterable(turns(11, 12)));

        compactor.compact(8L).block();

        verify(store).release(lease);
        assertNull(sent.get());
    }

    @Test
    void unknownModelFailsWithoutCallingAnyModel() {
        when(store.claim(eq(9L), any())).thenReturn(Mono.just(new ConversationMemoryStore.Lease(9L, "t", "gone", 1,
                ConversationMemory.EMPTY)));

        compactor.compact(9L).block();

        verify(store).fail(any(), eq("MEMORY_MODEL_UNAVAILABLE"), anyInt(), any(), any());
    }

    private static List<CompletedContextTurn> turns(long from, long to) {
        return LongStream.rangeClosed(from, to).mapToObj(seq -> new CompletedContextTurn("t" + seq,
                "问题" + seq + "：" + "细".repeat(300), "回答" + seq + "：" + "节".repeat(300),
                LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(seq), seq)).toList();
    }
}
