package org.example.ai_agent.service.impl.chat;

import org.example.ai_agent.config.performance.CacheConfig;
import org.example.ai_agent.dto.chat.ConversationWithPreview;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.repository.chat.ConversationRepository;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.example.ai_agent.service.ConversationService;
import org.example.ai_agent.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class ConversationServiceImpl implements ConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationServiceImpl.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SearchService searchService;

    public ConversationServiceImpl(ConversationRepository conversationRepository,
                                   MessageRepository messageRepository,
                                   SearchService searchService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.searchService = searchService;
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CacheNames.CONVERSATIONS, key = "#conversation.userId")
    public Mono<Conversation> createConversation(Conversation conversation) {
        logger.info("Creating conversation with title {}", conversation != null ? conversation.getTitle() : "null");

        return Mono.fromCallable(() -> {
                    if (conversation == null) {
                        throw new IllegalArgumentException("Conversation must not be null");
                    }
                    if (conversation.getUserId() == null) {
                        throw new IllegalArgumentException("User ID must not be null");
                    }
                    if (!StringUtils.hasText(conversation.getTitle())) {
                        throw new IllegalArgumentException("Conversation title must not be blank");
                    }

                    LocalDateTime now = LocalDateTime.now();
                    conversation.setCreatedAt(now);
                    conversation.setUpdatedAt(now);
                    return conversation;
                })
                .flatMap(conversationRepository::save)
                .doOnSuccess(savedConversation ->
                        logger.info("Conversation created successfully, id={}", savedConversation.getId()))
                .doOnError(error -> logger.error("Failed to create conversation", error));
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CacheNames.CONVERSATIONS, allEntries = true)
    public Mono<Message> addMessageToConversation(Message message) {
        logger.info("Adding message to conversation {}", message != null ? message.getConversationId() : "null");

        if (message == null || message.getConversationId() == null ||
                !StringUtils.hasText(message.getContent()) || !StringUtils.hasText(message.getSender())) {
            return Mono.error(new IllegalArgumentException("Message payload is invalid"));
        }

        message.setTimestampIfNull();

        return messageRepository.save(message)
                .flatMap(savedMessage -> updateConversationTimestamp(savedMessage.getConversationId())
                        .then(searchService.indexConversation(savedMessage.getConversationId()))
                        .thenReturn(savedMessage))
                .doOnSuccess(savedMessage -> logger.info("Message saved successfully, id={}", savedMessage.getId()))
                .doOnError(error -> logger.error("Failed to add message", error));
    }

    private Mono<Conversation> updateConversationTimestamp(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .flatMap(conversation -> {
                    conversation.setUpdatedAt(LocalDateTime.now());
                    return conversationRepository.save(conversation);
                });
    }

    @Override
    public Flux<Message> getMessagesByConversationId(Long conversationId) {
        logger.info("Loading messages for conversation {}", conversationId);

        return Mono.fromCallable(() -> {
                    if (conversationId == null || conversationId <= 0) {
                        throw new IllegalArgumentException("Conversation ID must be greater than 0");
                    }
                    return conversationId;
                })
                .flatMapMany(validatedId -> conversationRepository.findById(validatedId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversation does not exist: " + validatedId)))
                        .flatMapMany(conversation -> messageRepository.findByConversationIdOrderByTimestampAsc(conversation.getId())))
                .doOnError(error -> logger.error("Failed to load messages for conversation {}", conversationId, error));
    }

    @Override
    public Flux<Message> getMessagesByConversationId(Long conversationId, Long userId) {
        return getConversationById(conversationId, userId)
                .flatMapMany(conversation -> messageRepository.findByConversationIdOrderByTimestampAsc(conversation.getId()));
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CacheNames.CONVERSATIONS, key = "#userId")
    public Flux<ConversationWithPreview> getRecentConversationsWithPreview(Long userId) {
        logger.info("Loading recent conversations for user {}", userId);

        return Mono.fromCallable(() -> {
                    if (userId == null || userId <= 0) {
                        throw new IllegalArgumentException("User ID must be greater than 0");
                    }
                    return userId;
                })
                .flatMapMany(validatedUserId -> conversationRepository.findTop5ByUserIdOrderByUpdatedAtDesc(validatedUserId)
                        .flatMap(conversation -> messageRepository
                                .findFirstByConversationIdAndSenderOrderByTimestampAsc(conversation.getId(), "user")
                                .timeout(Duration.ofSeconds(5))
                                .map(firstUserMessage -> toPreview(conversation, firstUserMessage.getContent()))
                                .switchIfEmpty(Mono.just(toPreview(conversation, null)))
                                .onErrorResume(error -> {
                                    logger.warn("Failed to build preview for conversation {}", conversation.getId(), error);
                                    return Mono.just(toPreview(conversation, "Preview unavailable"));
                                }))
                        .timeout(Duration.ofSeconds(10))
                        .onErrorResume(error -> {
                            logger.error("Failed to load recent conversations for user {}", userId, error);
                            return Flux.empty();
                        }));
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CacheNames.CONVERSATIONS, allEntries = true)
    public Mono<Void> deleteConversation(Long conversationId, Long userId) {
        logger.info("Deleting conversation {} for user {}", conversationId, userId);

        return getConversationById(conversationId, userId)
                .flatMap(conversation -> messageRepository.deleteByConversationId(conversation.getId())
                        .then(conversationRepository.deleteById(conversation.getId()))
                        .then(searchService.deleteConversationIndex(conversation.getId())
                                .doOnError(error -> logger.error("Failed to delete conversation index {}", conversation.getId(), error))
                                .onErrorResume(error -> Mono.empty())))
                .then();
    }

    @Override
    public Mono<Conversation> getConversationById(Long conversationId) {
        logger.info("Loading conversation {}", conversationId);

        return Mono.fromCallable(() -> {
                    if (conversationId == null || conversationId <= 0) {
                        throw new IllegalArgumentException("Conversation ID must be greater than 0");
                    }
                    return conversationId;
                })
                .flatMap(validatedId -> conversationRepository.findById(validatedId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversation does not exist: " + validatedId))))
                .doOnSuccess(conversation -> logger.info("Loaded conversation {}", conversation.getId()))
                .doOnError(error -> logger.error("Failed to load conversation {}", conversationId, error));
    }

    @Override
    public Mono<Conversation> getConversationById(Long conversationId, Long userId) {
        logger.info("Checking conversation {} ownership for user {}", conversationId, userId);

        if (conversationId == null || conversationId <= 0) {
            return Mono.error(new IllegalArgumentException("Conversation ID must be greater than 0"));
        }
        if (userId == null || userId <= 0) {
            return Mono.error(new IllegalArgumentException("User ID must be greater than 0"));
        }

        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .switchIfEmpty(Mono.error(new SecurityException("Access to this conversation is forbidden")))
                .doOnSuccess(conversation -> logger.info("Conversation {} ownership verified", conversation.getId()))
                .doOnError(error -> logger.warn("Conversation {} ownership check failed: {}", conversationId, error.getMessage()));
    }

    private ConversationWithPreview toPreview(Conversation conversation, String firstUserMessage) {
        ConversationWithPreview preview = new ConversationWithPreview();
        preview.setId(conversation.getId());
        preview.setTitle(conversation.getTitle());
        preview.setCreatedAt(conversation.getCreatedAt());
        preview.setUpdatedAt(conversation.getUpdatedAt());
        preview.setUserId(conversation.getUserId());
        preview.setFirstUserMessage(firstUserMessage);
        return preview;
    }
}
