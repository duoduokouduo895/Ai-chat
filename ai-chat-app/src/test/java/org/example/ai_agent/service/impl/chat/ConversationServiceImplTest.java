package org.example.ai_agent.service.impl.chat;

import org.example.ai_agent.dto.chat.ConversationWithPreview;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.repository.chat.ConversationRepository;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.example.ai_agent.repository.chat.RedisChatMemoryStore;
import org.example.ai_agent.service.impl.search.ConversationSearchIndexQueue;
import org.springframework.cache.Cache;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationSearchIndexQueue searchIndexQueue;

    @Mock
    private org.springframework.cache.CacheManager cacheManager;

    @Mock
    private Cache conversationsCache;

    @Mock
    private RedisChatMemoryStore chatMemoryStore;

    @Test
    void getRecentConversationsUsesSingleAggregateRepositoryQuery() {
        ConversationWithPreview preview = new ConversationWithPreview(
                10L,
                "New Conversation",
                LocalDateTime.of(2026, 5, 30, 16, 0),
                LocalDateTime.of(2026, 5, 30, 16, 1),
                42L,
                "下午好",
                "有什么我可以帮助您的吗？"
        );
        when(conversationRepository.findRecentPreviewsByUserId(42L))
                .thenReturn(Flux.just(preview));

        ConversationServiceImpl service = new ConversationServiceImpl(
                conversationRepository,
                messageRepository,
                searchIndexQueue,
                cacheManager,
                chatMemoryStore, org.mockito.Mockito.mock(ChatRetrievalReader.class)
        );

        StepVerifier.create(service.getRecentConversationsWithPreview(42L))
                .expectNext(preview)
                .verifyComplete();

        verify(conversationRepository).findRecentPreviewsByUserId(42L);
        verifyNoInteractions(messageRepository);
    }

    @Test
    void getAllRecentConversationsUsesSingleAggregateRepositoryQuery() {
        ConversationWithPreview preview = new ConversationWithPreview();
        preview.setId(11L);
        when(conversationRepository.findAllPreviewsForAdmin(20, 0L))
                .thenReturn(Flux.just(preview));

        ConversationServiceImpl service = createService();

        StepVerifier.create(service.getAllRecentConversations(0, 20))
                .expectNext(preview)
                .verifyComplete();

        verify(conversationRepository).findAllPreviewsForAdmin(20, 0L);
        verifyNoInteractions(messageRepository);
    }

    @Test
    void getAllRecentConversationsPageUsesDatabasePaginationAndCount() {
        ConversationWithPreview preview = new ConversationWithPreview();
        preview.setId(18L);
        when(conversationRepository.findAdminPage("42", 10, 10L))
                .thenReturn(Flux.just(preview));
        when(conversationRepository.countAdminPage("42")).thenReturn(Mono.just(1L));

        StepVerifier.create(createService().getAllRecentConversationsPage(1, 10, " 42 "))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertEquals(1L, result.getTotal());
                    org.junit.jupiter.api.Assertions.assertEquals(18L, result.getItems().get(0).getId());
                    org.junit.jupiter.api.Assertions.assertEquals(1L, result.getSummary().get("conversationCount"));
                })
                .verifyComplete();
    }

    @Test
    void addMessageEnqueuesIndexRefreshAfterDatabaseWrite() {
        Message message = new Message();
        message.setId(20L);
        message.setConversationId(12L);
        message.setSender("user");
        message.setContent("你好");

        Conversation conversation = new Conversation();
        conversation.setId(12L);
        when(messageRepository.save(message)).thenReturn(Mono.just(message));
        when(conversationRepository.findById(12L)).thenReturn(Mono.just(conversation));
        when(conversationRepository.save(conversation)).thenReturn(Mono.just(conversation));

        StepVerifier.create(createService().addMessageToConversation(message))
                .expectNext(message)
                .verifyComplete();

        verify(searchIndexQueue).enqueueUpsert(12L);
    }

    @Test
    void addMessageReturnsExistingRowWhenDeduplicationKeyAlreadyExists() {
        Message retriedMessage = new Message();
        retriedMessage.setConversationId(12L);
        retriedMessage.setSender("ai");
        retriedMessage.setContent("你好");
        retriedMessage.setDeduplicationKey("ai:42:12:request-1");

        Message existingMessage = new Message();
        existingMessage.setId(20L);
        existingMessage.setConversationId(12L);
        existingMessage.setSender("ai");
        existingMessage.setContent("你好");
        existingMessage.setDeduplicationKey("ai:42:12:request-1");

        Conversation conversation = new Conversation();
        conversation.setId(12L);
        when(messageRepository.save(retriedMessage))
                .thenReturn(Mono.error(new DataIntegrityViolationException("duplicate key")));
        when(messageRepository.findByDeduplicationKey("ai:42:12:request-1"))
                .thenReturn(Mono.just(existingMessage));
        when(conversationRepository.findById(12L)).thenReturn(Mono.just(conversation));
        when(conversationRepository.save(conversation)).thenReturn(Mono.just(conversation));

        StepVerifier.create(createService().addMessageToConversation(retriedMessage))
                .expectNext(existingMessage)
                .verifyComplete();

        verify(messageRepository).findByDeduplicationKey("ai:42:12:request-1");
        verify(searchIndexQueue).enqueueUpsert(12L);
    }

    @Test
    void deleteConversationEnqueuesIndexDeletionAfterDatabaseDelete() {
        Conversation conversation = new Conversation();
        conversation.setId(13L);
        conversation.setUserId(42L);
        when(conversationRepository.findByIdAndUserId(13L, 42L)).thenReturn(Mono.just(conversation));
        when(messageRepository.deleteByConversationId(13L)).thenReturn(Mono.empty());
        when(conversationRepository.deleteById(13L)).thenReturn(Mono.empty());
        when(cacheManager.getCache("conversations")).thenReturn(conversationsCache);

        StepVerifier.create(createService().deleteConversation(13L, 42L))
                .verifyComplete();

        verify(searchIndexQueue).enqueueDelete(13L);
        verify(conversationsCache).evict("42");
        verify(conversationsCache, never()).clear();
        verify(chatMemoryStore).deleteConversationMessages(13L);
    }

    private ConversationServiceImpl createService() {
        return new ConversationServiceImpl(conversationRepository, messageRepository, searchIndexQueue, cacheManager,
                chatMemoryStore, org.mockito.Mockito.mock(ChatRetrievalReader.class));
    }
}
