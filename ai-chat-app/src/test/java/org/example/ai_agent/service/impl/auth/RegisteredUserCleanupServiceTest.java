package org.example.ai_agent.service.impl.auth;

import org.example.ai_agent.config.security.JwtConfig;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.KnowledgeBase;
import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.chat.ConversationRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeBaseRepository;
import org.example.ai_agent.service.ConversationService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.SearchService;
import org.example.ai_agent.util.TokenRedisKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisteredUserCleanupServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationService conversationService;
    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Mock
    private SearchService searchService;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;
    @Mock
    private ReactiveSetOperations<String, String> setOperations;
    @Mock
    private JwtConfig jwtConfig;

    @Test
    void cleanupRevokesTokensAndDeletesUserOwnedResources() {
        User user = new User();
        user.setUserId(42L);
        Conversation conversation = new Conversation();
        conversation.setId(10L);
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(20L);

        when(jwtConfig.getExpiration()).thenReturn(3_600_000L);
        when(jwtConfig.getRefreshWindow()).thenReturn(300_000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.set(org.mockito.ArgumentMatchers.eq(TokenRedisKeys.userRevoked(42L)),
                org.mockito.ArgumentMatchers.eq("1"), any(Duration.class))).thenReturn(Mono.just(true));
        when(setOperations.members(TokenRedisKeys.userJtis(42L))).thenReturn(Flux.just("jti-1"));
        when(redisTemplate.delete(TokenRedisKeys.jti("jti-1"), TokenRedisKeys.refreshJti("jti-1")))
                .thenReturn(Mono.just(1L));
        when(redisTemplate.delete(TokenRedisKeys.userJtis(42L))).thenReturn(Mono.just(1L));
        when(conversationRepository.findByUserIdOrderByUpdatedAtDesc(42L)).thenReturn(Flux.just(conversation));
        when(conversationService.deleteConversationAsAdmin(10L)).thenReturn(Mono.empty());
        when(knowledgeBaseRepository.findByCreatorId(42L)).thenReturn(Flux.just(knowledgeBase));
        when(knowledgeBaseService.deleteKnowledgeBase(20L, 42L, true)).thenReturn(Mono.empty());
        when(searchService.clearSearchHistory(42L)).thenReturn(Mono.empty());

        StepVerifier.create(service().cleanup(user))
                .verifyComplete();

        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq(TokenRedisKeys.userRevoked(42L)),
                org.mockito.ArgumentMatchers.eq("1"), any(Duration.class));
        verify(redisTemplate).delete(TokenRedisKeys.jti("jti-1"), TokenRedisKeys.refreshJti("jti-1"));
        verify(conversationService).deleteConversationAsAdmin(10L);
        verify(knowledgeBaseService).deleteKnowledgeBase(20L, 42L, true);
        verify(searchService).clearSearchHistory(42L);
    }

    private RegisteredUserCleanupService service() {
        return new RegisteredUserCleanupService(
                conversationRepository,
                conversationService,
                knowledgeBaseRepository,
                knowledgeBaseService,
                searchService,
                redisTemplate,
                jwtConfig
        );
    }
}
