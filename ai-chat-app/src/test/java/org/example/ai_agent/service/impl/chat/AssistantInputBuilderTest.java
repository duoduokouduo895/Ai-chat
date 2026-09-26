package org.example.ai_agent.service.impl.chat;

import org.example.ai_agent.config.ai.ModelCatalogProperties;
import org.example.ai_agent.conversation.CompletedContextTurn;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchResultDto.KnowledgeSearchMatch;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.example.ai_agent.service.RagService;
import org.example.ai_agent.service.assistant.RetrievalQueryRewriter;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantInputBuilderTest {
    @Test
    void retrievesWithRewrittenQuestionButPreservesOriginalInputAndLoadsHistoryOnce() {
        var repository = mock(MessageRepository.class);
        var rewriter = mock(RetrievalQueryRewriter.class);
        var rag = mock(RagService.class);
        var loader = new ConversationContextLoader(repository, new ModelCatalogProperties());
        var request = ChatRequest.builder().conversationId(8L).userId(42L).model("chat-fast")
                .prompt("它支持多少并发连接？").knowledgeBaseIds(List.of(7L)).build();
        var history = List.of(new CompletedContextTurn("previous", "介绍 A 产品", "A 产品介绍", LocalDateTime.now()));
        when(repository.findCompletedContext(8L)).thenReturn(Flux.fromIterable(history));
        when(repository.findLegacyContext(8L)).thenReturn(Flux.empty());
        when(rewriter.rewrite(request, history)).thenReturn(Mono.just("A 产品支持多少并发连接？"));
        var match = new KnowledgeSearchMatch();
        match.setDocumentTitle("A 产品手册");
        match.setContent("支持 100 个并发连接。");
        when(rag.retrieveContext("A 产品支持多少并发连接？", List.of(7L),
                request.getMaxRetrievedDocs(), request.getMinSimilarity(), 42L, false))
                .thenReturn(Mono.just(new RagService.RetrievalContext("A 产品支持多少并发连接？",
                        List.of(match), match.getContent(), 1, 1L)));

        StepVerifier.create(new AssistantInputBuilder(rag, loader, rewriter).build(request))
                .assertNext(input -> {
                    assertTrue(input.contains("用户问题：\n它支持多少并发连接？"));
                    assertTrue(input.contains("支持 100 个并发连接。"));
                }).verifyComplete();

        assertEquals("它支持多少并发连接？", request.getPrompt());
        assertEquals(history, request.getContextTurns());
        verify(repository, times(1)).findCompletedContext(8L);
        verify(repository, times(1)).findLegacyContext(8L);
        verify(rag).retrieveContext("A 产品支持多少并发连接？", List.of(7L),
                request.getMaxRetrievedDocs(), request.getMinSimilarity(), 42L, false);
        verifyNoMoreInteractions(rag);
    }

    @Test
    void ordinaryChatDoesNotRewriteOrRetrieve() {
        var repository = mock(MessageRepository.class);
        when(repository.findCompletedContext(8L)).thenReturn(Flux.empty());
        when(repository.findLegacyContext(8L)).thenReturn(Flux.empty());
        var rag = mock(RagService.class);
        var rewriter = mock(RetrievalQueryRewriter.class);
        var request = ChatRequest.builder().conversationId(8L).model("chat-fast").prompt("你好").build();

        StepVerifier.create(new AssistantInputBuilder(rag,
                        new ConversationContextLoader(repository, new ModelCatalogProperties()), rewriter).build(request))
                .expectNext("你好").verifyComplete();
        verifyNoInteractions(rag, rewriter);
    }
}
