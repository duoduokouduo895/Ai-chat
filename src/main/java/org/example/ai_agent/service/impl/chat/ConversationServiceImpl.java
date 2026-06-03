package org.example.ai_agent.service.impl.chat;

import org.example.ai_agent.config.performance.CacheConfig;
import org.example.ai_agent.dto.admin.AdminPageDto;
import org.example.ai_agent.dto.chat.ConversationWithPreview;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.repository.chat.ConversationRepository;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.example.ai_agent.repository.chat.RedisChatMemeoryStore;
import org.example.ai_agent.service.ConversationService;
import org.example.ai_agent.service.impl.search.ConversationSearchIndexQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ConversationServiceImpl implements ConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationServiceImpl.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationSearchIndexQueue searchIndexQueue;
    private final CacheManager cacheManager;
    private final RedisChatMemeoryStore chatMemoryStore;

    public ConversationServiceImpl(ConversationRepository conversationRepository,
                                   MessageRepository messageRepository,
                                   ConversationSearchIndexQueue searchIndexQueue,
                                   CacheManager cacheManager,
                                   RedisChatMemeoryStore chatMemoryStore) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.searchIndexQueue = searchIndexQueue;
        this.cacheManager = cacheManager;
        this.chatMemoryStore = chatMemoryStore;
    }

    @Override
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
                .flatMap(savedConversation -> evictRecentConversations(savedConversation.getUserId())
                        .thenReturn(savedConversation))
                .doOnSuccess(savedConversation ->
                        logger.info("Conversation created successfully, id={}", savedConversation.getId()))
                .doOnError(error -> logger.error("Failed to create conversation", error));
    }

    @Override
    public Mono<Message> addMessageToConversation(Message message) {
        logger.info("Adding message to conversation {}", message != null ? message.getConversationId() : "null");

        if (message == null || message.getConversationId() == null ||
                !StringUtils.hasText(message.getContent()) || !StringUtils.hasText(message.getSender())) {
            return Mono.error(new IllegalArgumentException("Message payload is invalid"));
        }

        message.setTimestampIfNull();

        return saveMessageIdempotently(message)
                .flatMap(savedMessage -> updateConversationTimestamp(savedMessage.getConversationId())
                        .flatMap(conversation -> evictRecentConversations(conversation.getUserId()))
                        .thenReturn(savedMessage))
                .doOnSuccess(savedMessage -> searchIndexQueue.enqueueUpsert(savedMessage.getConversationId()))
                .doOnSuccess(savedMessage -> logger.info("Message saved successfully, id={}", savedMessage.getId()))
                .doOnError(error -> logger.error("Failed to add message", error));
    }

    private Mono<Message> saveMessageIdempotently(Message message) {
        return messageRepository.save(message)
                .onErrorResume(DataIntegrityViolationException.class, error -> {
                    if (!StringUtils.hasText(message.getDeduplicationKey())) {
                        return Mono.error(error);
                    }
                    logger.info("Message already exists for deduplication key, returning existing row");
                    return messageRepository.findByDeduplicationKey(message.getDeduplicationKey())
                            .switchIfEmpty(Mono.error(error));
                });
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
                .flatMapMany(conversation -> messageRepository.findByConversationIdOrderByTimestampAsc(conversation.getId())
                        .switchIfEmpty(Flux.defer(() -> {
                            logger.warn("Conversation {} for user {} exists but has no message records", conversation.getId(), userId);
                            return Flux.empty();
                        })));
    }

    @Override
    public Flux<ConversationWithPreview> getRecentConversationsWithPreview(Long userId) {
        logger.info("Loading recent conversations for user {}", userId);
        if (userId == null || userId <= 0) {
            return Flux.error(new IllegalArgumentException("User ID must be greater than 0"));
        }

        return readRecentConversations(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    logger.debug("Recent conversations cache miss for user {}, loading from database", userId);
                    return conversationRepository.findRecentPreviewsByUserId(userId)
                            .timeout(Duration.ofSeconds(10))
                            .collectList()
                            .flatMap(list -> writeRecentConversations(userId, list).thenReturn(list));
                }))
                .flatMapMany(Flux::fromIterable)
                .onErrorResume(error -> {
                    logger.error("Failed to load recent conversations for user {}", userId, error);
                    return Flux.empty();
                });
    }

    @Override
    public Flux<ConversationWithPreview> getAllRecentConversations(Integer page, Integer size) {
        logger.info("Loading admin conversation list, page={}, size={}", page, size);

        return Mono.fromCallable(() -> {
                    int finalPage = page != null && page >= 0 ? page : 0;
                    int finalSize = size != null && size > 0 && size <= 200 ? size : 20;
                    return new PageSlice(finalSize, (long) finalPage * finalSize);
                })
                .flatMapMany(slice -> conversationRepository.findAllPreviewsForAdmin(slice.limit(), slice.offset()))
                .doOnError(error -> logger.error("Failed to load admin conversation list", error));
    }

    @Override
    public Mono<AdminPageDto<ConversationWithPreview>> getAllRecentConversationsPage(Integer page, Integer size,
                                                                                     String keyword) {
        int finalPage = page != null && page >= 0 ? page : 0;
        int finalSize = size != null && size > 0 && size <= 100 ? size : 10;
        String finalKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        long offset = (long) finalPage * finalSize;

        return Mono.zip(
                conversationRepository.findAdminPage(finalKeyword, finalSize, offset).collectList(),
                conversationRepository.countAdminPage(finalKeyword)
        ).map(tuple -> new AdminPageDto<>(
                tuple.getT1(),
                tuple.getT2(),
                Map.of("conversationCount", tuple.getT2())
        )).doOnSuccess(result -> logger.info("Loaded admin conversation page, page={}, size={}, total={}",
                finalPage, finalSize, result.getTotal()));
    }

    @Override
    public Flux<ConversationWithPreview> getConversationsForAdminByUserId(Long userId, Integer page, Integer size) {
        logger.info("Loading admin conversation list for user {}, page={}, size={}", userId, page, size);

        return Mono.fromCallable(() -> {
                    int finalPage = page != null && page >= 0 ? page : 0;
                    int finalSize = size != null && size > 0 && size <= 100 ? size : 10;
                    return new PageSlice(finalSize, (long) finalPage * finalSize);
                })
                .flatMapMany(slice -> conversationRepository.findPreviewsForAdminByUserId(
                        userId, slice.limit(), slice.offset()))
                .doOnError(error -> logger.error("Failed to load admin conversation list for user {}", userId, error));
    }

    @Override
    public Mono<Void> deleteConversation(Long conversationId, Long userId) {
        logger.info("Deleting conversation {} for user {}", conversationId, userId);

        return getConversationById(conversationId, userId)
                .flatMap(conversation -> messageRepository.deleteByConversationId(conversation.getId())
                        .then(conversationRepository.deleteById(conversation.getId()))
                        .then(evictRecentConversations(conversation.getUserId()))
                        .then(deleteChatMemory(conversation.getId()))
                        .doOnSuccess(ignored -> searchIndexQueue.enqueueDelete(conversation.getId())))
                .then();
    }

    @Override
    public Mono<Void> deleteConversationAsAdmin(Long conversationId) {
        logger.info("Admin deleting conversation {}", conversationId);

        return getConversationById(conversationId)
                .flatMap(conversation -> messageRepository.deleteByConversationId(conversation.getId())
                        .then(conversationRepository.deleteById(conversation.getId()))
                        .then(evictRecentConversations(conversation.getUserId()))
                        .then(deleteChatMemory(conversation.getId()))
                        .doOnSuccess(ignored -> searchIndexQueue.enqueueDelete(conversation.getId())))
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

    @SuppressWarnings("unchecked")
    private Mono<List<ConversationWithPreview>> readRecentConversations(Long userId) {
        Cache cache = recentConversationsCache();
        if (cache == null) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> cache.get(userId.toString(), List.class))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(cachedList -> {
                    if (cachedList == null) {
                        return Mono.empty();
                    }
                    logger.debug("Recent conversations cache hit for user {}", userId);
                    return Mono.just((List<ConversationWithPreview>) cachedList);
                });
    }

    private Mono<Void> writeRecentConversations(Long userId, List<ConversationWithPreview> conversations) {
        Cache cache = recentConversationsCache();
        if (cache == null) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
                    cache.put(userId.toString(), conversations);
                    logger.debug("Recent conversations cache populated for user {}", userId);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Mono<Void> evictRecentConversations(Long userId) {
        Cache cache = recentConversationsCache();
        if (cache == null || userId == null) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
                    cache.evict(userId.toString());
                    logger.debug("Recent conversations cache evicted for user {}", userId);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Mono<Void> deleteChatMemory(Long conversationId) {
        return Mono.fromRunnable(() -> chatMemoryStore.deleteConversationMessages(conversationId))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Cache recentConversationsCache() {
        return cacheManager.getCache(CacheConfig.CacheNames.CONVERSATIONS);
    }

    private record PageSlice(int limit, long offset) {
    }
}
