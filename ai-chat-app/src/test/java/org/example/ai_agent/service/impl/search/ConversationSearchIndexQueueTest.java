package org.example.ai_agent.service.impl.search;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.ai_agent.service.SearchService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("minimal")
@Tag("high-risk")
class ConversationSearchIndexQueueTest {

    @Test
    void boundsPendingAndCoalescesRebuildUntilShutdown() {
        SearchService service = mock(SearchService.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ConversationSearchIndexQueue queue = new ConversationSearchIndexQueue(service, registry, 2, 10_000);
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        when(service.rebuildAllIndexes()).thenReturn(Mono.<Void>never().doOnCancel(() -> cancelled.set(true)));
        queue.enqueueUpsert(1L);
        queue.enqueueUpsert(2L);
        queue.enqueueUpsert(3L);
        queue.enqueueDelete(1L);
        org.junit.jupiter.api.Assertions.assertEquals(2, registry.get("search.index.queue.pending").gauge().value());
        org.junit.jupiter.api.Assertions.assertEquals(1, registry.counter("search.index.queue.overflow").count());
        queue.drainPendingTasks();
        queue.drainPendingTasks();
        verify(service).rebuildAllIndexes();
        queue.stop();
        org.junit.jupiter.api.Assertions.assertTrue(cancelled.get());
        queue.drainPendingTasks();
        verify(service).rebuildAllIndexes();
    }

    @Test
    void shouldCoalescePendingOperationsAndLetDeleteWin() {
        SearchService searchService = mock(SearchService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ConversationSearchIndexQueue queue = new ConversationSearchIndexQueue(
                searchService, meterRegistry, 4, 10_000);

        when(searchService.deleteConversationIndex(9L)).thenReturn(Mono.empty());

        queue.enqueueUpsert(9L);
        queue.enqueueUpsert(9L);
        queue.enqueueDelete(9L);
        queue.start();

        verify(searchService).deleteConversationIndex(9L);
        verify(searchService, never()).indexConversation(9L);
        org.junit.jupiter.api.Assertions.assertEquals(
                2.0d, meterRegistry.counter("search.index.queue.coalesced").count());
        org.junit.jupiter.api.Assertions.assertEquals(
                0.0d, meterRegistry.get("search.index.queue.pending").gauge().value());

        queue.stop();
    }
}
