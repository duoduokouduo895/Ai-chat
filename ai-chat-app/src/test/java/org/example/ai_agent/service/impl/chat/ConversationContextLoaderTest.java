package org.example.ai_agent.service.impl.chat;

import org.example.ai_agent.config.ai.ModelCatalogProperties;
import org.example.ai_agent.conversation.CompletedContextTurn;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationContextLoaderTest {
    @Test
    void dropsHistoryBeforeTrimmingLowestRankedEvidence() {
        var repository = mock(MessageRepository.class);
        var properties = new ModelCatalogProperties();
        var model = new ModelCatalogProperties.ModelConfig();
        model.setInputBudgetTokens(2100);
        properties.getModels().put("rag", model);
        var request = ChatRequest.builder().conversationId(8L).model("rag").prompt("current")
                .knowledgeBaseIds(List.of(1L)).build();
        var first = new org.example.ai_agent.dto.knowledge.KnowledgeSearchResultDto.KnowledgeSearchMatch();
        first.setDocumentTitle("first"); first.setContent("useful");
        var second = new org.example.ai_agent.dto.knowledge.KnowledgeSearchResultDto.KnowledgeSearchMatch();
        second.setDocumentTitle("second"); second.setContent("x".repeat(3000));
        var context = new org.example.ai_agent.service.RagService.RetrievalContext("current", List.of(first, second),
                "useful" + second.getContent(), 2, 1L);
        request.setRetrievalContext(context);
        when(repository.findCompletedContext(8L)).thenReturn(Flux.just(new CompletedContextTurn(
                "old", "history", "answer", LocalDateTime.of(2026, 1, 1, 0, 0))));
        when(repository.findLegacyContext(8L)).thenReturn(Flux.empty());
        StepVerifier.create(new ConversationContextLoader(repository, properties).prepare(request,
                        AssistantInputBuilder.buildKnowledgeAugmentedPrompt(request, context)))
                .assertNext(input -> {
                    assertTrue(input.contains("useful"));
                    assertFalse(input.contains("second"));
                    assertTrue(input.contains("current"));
                }).verifyComplete();
        assertTrue(request.getContextTurns().isEmpty());
        assertEquals(List.of(first), request.getRetrievalContext().relevantDocuments());
        assertTrue(request.getEstimatedInputTokens() <= 2100);
    }

    @Test
    void evictsWholeOldestTurnsAndKeepsCurrentQuestionOnce() {
        var repository = mock(MessageRepository.class);
        var properties = new ModelCatalogProperties();
        var model = new ModelCatalogProperties.ModelConfig();
        model.setInputBudgetTokens(2100);
        model.setToolOutputReserveTokens(128);
        properties.getModels().put("model-b", model);
        var request = ChatRequest.builder().conversationId(8L).model("model-b").prompt("current").build();
        var older = new CompletedContextTurn("old", "u".repeat(1200), "a".repeat(1200), LocalDateTime.of(2026, 1, 1, 0, 0));
        var newer = new CompletedContextTurn("new", "recent", "answer", older.completedAt().plusSeconds(1));
        when(repository.findCompletedContext(8L)).thenReturn(Flux.just(newer, older));
        when(repository.findLegacyContext(8L)).thenReturn(Flux.empty());
        StepVerifier.create(new ConversationContextLoader(repository, properties).prepare(request, "current"))
                .expectNext("current").verifyComplete();
        assertEquals(List.of(newer), request.getContextTurns());
        assertTrue(request.getEstimatedInputTokens() <= 2100);
    }

    @Test
    void rejectsOversizedQuestionWithoutTruncation() {
        var repository = mock(MessageRepository.class);
        when(repository.findCompletedContext(8L)).thenReturn(Flux.empty());
        when(repository.findLegacyContext(8L)).thenReturn(Flux.empty());
        var question = "字".repeat(4000);
        var request = ChatRequest.builder().conversationId(8L).model("test").prompt(question).build();
        StepVerifier.create(new ConversationContextLoader(repository, new ModelCatalogProperties()).prepare(request, question))
                .expectError(ChatStreamClientException.class).verify();
        assertEquals(question, request.getPrompt());
        assertNull(request.getContextTurns());
    }

    @Test
    void completedTurnBoundaryPreventsGuessingALegacyPairAcrossIt() {
        assertTrue(ConversationContextLoader.legacyPairs(List.of(message(1, "user", "a"),
                message(2, "system", "a"), message(3, "ai", "a"))).isEmpty());
    }

    @Test
    void onlyPairsAdjacentLegacyMessagesWithMatchingModel() {
        var user = message(1, "user", "a");
        var answer = message(2, "ai", "a");
        var orphan = message(3, "ai", "a");
        var failed = message(4, "user", "a");
        var mismatched = message(5, "ai", "b");
        var result = ConversationContextLoader.legacyPairs(List.of(mismatched, failed, orphan, answer, user));
        assertEquals(1, result.size());
        assertEquals("legacy-2", result.getFirst().turnId());
    }

    private Message message(long id, String sender, String model) {
        var message = new Message();
        message.setId(id);
        message.setSender(sender);
        message.setModel(model);
        message.setContent(sender + id);
        message.setTimestamp(LocalDateTime.of(2026, 1, 1, 0, 0));
        return message;
    }
}
