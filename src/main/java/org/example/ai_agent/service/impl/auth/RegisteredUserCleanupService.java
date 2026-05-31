package org.example.ai_agent.service.impl.auth;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.security.JwtConfig;
import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.chat.ConversationRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeBaseRepository;
import org.example.ai_agent.service.ConversationService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.SearchService;
import org.example.ai_agent.util.TokenRedisKeys;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
public class RegisteredUserCleanupService {

    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final SearchService searchService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final JwtConfig jwtConfig;

    public RegisteredUserCleanupService(ConversationRepository conversationRepository,
                                        ConversationService conversationService,
                                        KnowledgeBaseRepository knowledgeBaseRepository,
                                        KnowledgeBaseService knowledgeBaseService,
                                        SearchService searchService,
                                        ReactiveStringRedisTemplate redisTemplate,
                                        JwtConfig jwtConfig) {
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeBaseService = knowledgeBaseService;
        this.searchService = searchService;
        this.redisTemplate = redisTemplate;
        this.jwtConfig = jwtConfig;
    }

    public Mono<Void> cleanup(User user) {
        if (user == null || user.getUserId() == null) {
            return Mono.error(new IllegalArgumentException("用户不存在，无法清理关联资源"));
        }

        Long userId = user.getUserId();
        return revokeUserTokens(userId)
                .then(Mono.whenDelayError(
                        deleteConversations(userId),
                        deleteKnowledgeBases(userId),
                        searchService.clearSearchHistory(userId)
                ))
                .doOnSuccess(unused -> log.info("注册用户关联资源清理完成: userId={}", userId))
                .doOnError(error -> log.error("注册用户关联资源清理失败: userId={}, error={}",
                        userId, error.getMessage(), error));
    }

    private Mono<Void> deleteConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .concatMap(conversation -> conversationService.deleteConversationAsAdmin(conversation.getId()))
                .then();
    }

    private Mono<Void> deleteKnowledgeBases(Long userId) {
        return knowledgeBaseRepository.findByCreatorId(userId)
                .concatMap(knowledgeBase -> knowledgeBaseService.deleteKnowledgeBase(knowledgeBase.getId(), userId, true))
                .then();
    }

    private Mono<Void> revokeUserTokens(Long userId) {
        Duration markerTtl = Duration.ofMillis(jwtConfig.getExpiration() + jwtConfig.getRefreshWindow());
        String userJtisKey = TokenRedisKeys.userJtis(userId);

        return redisTemplate.opsForValue()
                .set(TokenRedisKeys.userRevoked(userId), "1", markerTtl)
                .thenMany(redisTemplate.opsForSet().members(userJtisKey))
                .flatMap(jti -> redisTemplate.delete(TokenRedisKeys.jti(jti)))
                .then(redisTemplate.delete(userJtisKey))
                .then();
    }
}
