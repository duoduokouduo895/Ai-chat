package org.example.ai_agent.service.impl.search;

import org.example.ai_agent.document.ConversationDocument;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.repository.chat.ConversationRepository;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.example.ai_agent.repository.search.ConversationDocumentRepository;
import org.example.ai_agent.repository.search.SearchHistoryRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("minimal")
@Tag("high-risk")
class SearchServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ConversationDocumentRepository conversationDocumentRepository;
    @Mock
    private ReactiveElasticsearchOperations elasticsearchOperations;
    @Mock
    private SearchHistoryRepository searchHistoryRepository;

    @Test
    void rebuildAllIndexes_shouldUpsertBeforePruningWithoutDeletingEverything() {
        SearchServiceImpl service = new SearchServiceImpl(
                conversationRepository,
                messageRepository,
                conversationDocumentRepository,
                elasticsearchOperations,
                searchHistoryRepository);

        Conversation conversation = new Conversation();
        conversation.setId(9L);
        conversation.setUserId(42L);
        conversation.setTitle("测试会话");
        conversation.setCreatedAt(LocalDateTime.of(2026, 5, 30, 12, 0));

        when(conversationRepository.findAllConversationsFromRegisteredUsers())
                .thenReturn(Flux.just(conversation));
        when(messageRepository.findByConversationIdOrderByTimestampAsc(9L)).thenReturn(Flux.empty());
        when(conversationDocumentRepository.save(any(ConversationDocument.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(conversationDocumentRepository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(service.rebuildAllIndexes())
                .verifyComplete();

        verify(conversationDocumentRepository, never()).deleteAll();
        verify(conversationDocumentRepository).save(any(ConversationDocument.class));
    }

    @Test
    void getSearchSuggestions_shouldScopePrefixAndDeduplicateRecentQueries() {
        SearchServiceImpl service = new SearchServiceImpl(
                conversationRepository,
                messageRepository,
                conversationDocumentRepository,
                elasticsearchOperations,
                searchHistoryRepository);

        when(searchHistoryRepository.findSuggestionQueriesByUserId(42L, "合", 5))
                .thenReturn(Flux.just("合同", "合同", "合同模板"));

        StepVerifier.create(service.getSearchSuggestions(42L, " 合 ", 5))
                .expectNext(List.of("合同", "合同模板"))
                .verifyComplete();

        verify(searchHistoryRepository).findSuggestionQueriesByUserId(42L, "合", 5);
    }

    @Test
    void getSearchSuggestions_shouldNotQueryForInvalidActorOrPrefix() {
        SearchServiceImpl service = new SearchServiceImpl(
                conversationRepository,
                messageRepository,
                conversationDocumentRepository,
                elasticsearchOperations,
                searchHistoryRepository);

        StepVerifier.create(service.getSearchSuggestions(null, "合同", 5))
                .expectNext(List.of())
                .verifyComplete();
        StepVerifier.create(service.getSearchSuggestions(42L, " ", 5))
                .expectNext(List.of())
                .verifyComplete();

        verify(searchHistoryRepository, never()).findSuggestionQueriesByUserId(any(), any(), any(int.class));
    }

    @Test
    void getSearchSuggestions_shouldEscapeLikeWildcardsInThePrefix() {
        SearchServiceImpl service = new SearchServiceImpl(
                conversationRepository,
                messageRepository,
                conversationDocumentRepository,
                elasticsearchOperations,
                searchHistoryRepository);
        when(searchHistoryRepository.findSuggestionQueriesByUserId(42L, "100!%!_!!", 5))
                .thenReturn(Flux.just("100%_! 文档"));

        StepVerifier.create(service.getSearchSuggestions(42L, "100%_!", 5))
                .expectNext(List.of("100%_! 文档"))
                .verifyComplete();

        verify(searchHistoryRepository).findSuggestionQueriesByUserId(42L, "100!%!_!!", 5);
    }
}
