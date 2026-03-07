package org.example.ai_agent.service.impl.chat;

import org.example.ai_agent.config.performance.CacheConfig;
import org.example.ai_agent.dto.chat.ConversationWithPreview;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.repository.chat.ConversationRepository;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.example.ai_agent.repository.auth.UserRepository;
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

import java.time.LocalDateTime;

/**
 * 对话服务实现类
 * 负责处理对话和消息的创建、查询等业务逻辑
 *
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Service
public class ConversationServiceImpl implements ConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationServiceImpl.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository; //
    private final SearchService searchService; // 新增

    /**
     * 构造函数，注入依赖的Repository
     *
     * @param conversationRepository 对话数据访问层
     * @param messageRepository      消息数据访问层
     * @param userRepository         用户数据访问层
     * @param searchService          搜索服务
     */
    public ConversationServiceImpl(ConversationRepository conversationRepository,
                                 MessageRepository messageRepository,
                                 UserRepository userRepository,
                                 SearchService searchService) { // 新增
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.searchService = searchService; // 新增
    }

    /**
     * 创建新的对话
     */
    @Override
    @CacheEvict(cacheNames = CacheConfig.CacheNames.CONVERSATIONS, key = "#conversation.userId")
    public Mono<Conversation> createConversation(Conversation conversation) {
        logger.info("开始创建新对话，标题: {}", conversation != null ? conversation.getTitle() : "null");

        return Mono.fromCallable(() -> {
            // 参数验证
            if (conversation == null) {
                throw new IllegalArgumentException("对话对象不能为空");
            }

            if (conversation.getUserId() == null) {
                throw new IllegalArgumentException("用户ID不能为空");
            }

            if (!StringUtils.hasText(conversation.getTitle())) {
                throw new IllegalArgumentException("对话标题不能为空");
            }

            // 设置创建和更新时间
            LocalDateTime now = LocalDateTime.now();
            conversation.setCreatedAt(now);
            conversation.setUpdatedAt(now);

            return conversation;
        })
        .flatMap(validatedConversation -> {
            logger.debug("保存对话到数据库，用户ID: {}", validatedConversation.getUserId());
            return conversationRepository.save(validatedConversation);
        })
        .doOnSuccess(savedConversation -> {
            logger.info("对话创建成功，ID: {}, 标题: {}",
                       savedConversation.getId(), savedConversation.getTitle());
        })
        .doOnError(error -> {
            logger.error("创建对话失败: {}", error.getMessage(), error);
        });
    }

    /**
     * 向对话中添加消息
     *
     * @param message 要添加的消息对象
     * @return 添加成功的消息对象
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    @Override
    @CacheEvict(cacheNames = CacheConfig.CacheNames.CONVERSATIONS, allEntries = true)
    public Mono<Message> addMessageToConversation(Message message) {
        logger.info("开始向对话添加消息，对话ID: {}", message != null ? message.getConversationId() : "null");

        // 1. 基础参数验证
        if (message == null || message.getConversationId() == null ||
            !StringUtils.hasText(message.getContent()) || !StringUtils.hasText(message.getSender())) {
            return Mono.error(new IllegalArgumentException("消息参数无效"));
        }
        message.setTimestampIfNull();

        // 2. 保存消息并更新对话
        return messageRepository.save(message)
                .flatMap(savedMessage -> {
                    // 4. 异步更新对话时间戳并触发索引更新
                    return updateConversationTimestamp(savedMessage.getConversationId())
                            .then(searchService.indexConversation(savedMessage.getConversationId()))
                            .thenReturn(savedMessage);
                })
                .doOnSuccess(savedMessage -> logger.info("消息添加成功，ID: {}", savedMessage.getId()))
                .doOnError(error -> logger.error("添加消息失败: {}", error.getMessage(), error));
    }



    /**
     * 更新对话时间戳
     */
    private Mono<Conversation> updateConversationTimestamp(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .flatMap(conversation -> {
                    conversation.setUpdatedAt(LocalDateTime.now());
                    return conversationRepository.save(conversation);
                });
    }

    /**
     * 根据对话ID获取所有消息
     *
     * @param conversationId 对话ID
     * @return 按时间顺序排列的消息流
     * @throws IllegalArgumentException 当对话ID无效时抛出
     */
    @Override
    public Flux<Message> getMessagesByConversationId(Long conversationId) {
        logger.info("开始获取对话消息，对话ID: {}", conversationId);

        return Mono.fromCallable(() -> {
            // 参数验证
            if (conversationId == null) {
                throw new IllegalArgumentException("对话ID不能为空");
            }

            if (conversationId <= 0) {
                throw new IllegalArgumentException("对话ID必须大于0");
            }

            return conversationId;
        })
        .flatMapMany(validatedId -> {
            // 先验证对话是否存在
            return conversationRepository.findById(validatedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                    "对话不存在，ID: " + validatedId)))
                .flatMapMany(conversation -> {
                    logger.debug("查询对话消息，对话标题: {}", conversation.getTitle());
                    return messageRepository.findByConversationIdOrderByTimestampAsc(validatedId);
                });
        })
        .doOnComplete(() -> {
            logger.info("对话消息查询完成，对话ID: {}", conversationId);
        })
        .doOnError(error -> {
            logger.error("获取对话消息失败，对话ID: {}, 错误: {}", conversationId, error.getMessage(), error);
        });
    }


    /**
     * 获取用户最近的对话列表（包含预览信息）
     *
     * @param userId 用户ID
     * @return 包含预览信息的最近对话列表，按更新时间倒序排列
     * @throws IllegalArgumentException 当用户ID无效时抛出
     */
    @Override
    @Cacheable(cacheNames = CacheConfig.CacheNames.CONVERSATIONS, key = "#userId")
    public Flux<ConversationWithPreview> getRecentConversationsWithPreview(Long userId) {
        logger.info("开始获取用户最近对话（包含预览信息），用户ID: {}", userId);

        return Mono.fromCallable(() -> {
            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException("无效的用户ID");
            }
            return userId;
        })
        .flatMapMany(validatedUserId -> {
            // 使用更新时间降序排序，确保最近活跃的对话在前面
            return conversationRepository.findTop5ByUserIdOrderByUpdatedAtDesc(validatedUserId)
                .flatMap(conversation -> {
                    // 为每个对话查询第一条用户消息作为预览
                    return messageRepository.findFirstByConversationIdAndSenderOrderByTimestampAsc(
                            conversation.getId(), "user")
                        .timeout(java.time.Duration.ofSeconds(5)) // 添加超时控制
                        .map(firstUserMessage -> {
                            ConversationWithPreview preview = new ConversationWithPreview();
                            preview.setId(conversation.getId());
                            preview.setTitle(conversation.getTitle());
                            preview.setCreatedAt(conversation.getCreatedAt());
                            preview.setUpdatedAt(conversation.getUpdatedAt());
                            preview.setUserId(conversation.getUserId());
                            preview.setFirstUserMessage(firstUserMessage.getContent());
                            return preview;
                        })
                        .switchIfEmpty(Mono.fromCallable(() -> {
                            // 如果对话中没有用户消息，则预览为空
                            ConversationWithPreview preview = new ConversationWithPreview();
                            preview.setId(conversation.getId());
                            preview.setTitle(conversation.getTitle());
                            preview.setCreatedAt(conversation.getCreatedAt());
                            preview.setUpdatedAt(conversation.getUpdatedAt());
                            preview.setUserId(conversation.getUserId());
                            preview.setFirstUserMessage(null);
                            return preview;
                        }))
                        .onErrorResume(error -> {
                            // 对单个对话查询失败时，返回基本信息
                            logger.warn("获取对话 {} 预览信息失败: {}", conversation.getId(), error.getMessage());
                            ConversationWithPreview preview = new ConversationWithPreview();
                            preview.setId(conversation.getId());
                            preview.setTitle(conversation.getTitle());
                            preview.setCreatedAt(conversation.getCreatedAt());
                            preview.setUpdatedAt(conversation.getUpdatedAt());
                            preview.setUserId(conversation.getUserId());
                            preview.setFirstUserMessage("预览加载失败");
                            return Mono.just(preview);
                        });
                })
                .timeout(java.time.Duration.ofSeconds(10)) // 整体查询超时
                .onErrorResume(error -> {
                    logger.error("获取用户最近对话失败，用户ID: {}, 错误: {}", userId, error.getMessage());
                    // 返回空结果而不是错误，避免前端500错误
                    return Flux.empty();
                });
        })
        .doOnComplete(() -> {
            logger.info("用户最近对话（包含预览信息）查询完成，用户ID: {}", userId);
        })
        .doOnError(error -> {
            logger.error("获取用户最近对话（包含预览信息）失败，用户ID: {}, 错误: {}",
                       userId, error.getMessage(), error);
        });
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CacheNames.CONVERSATIONS, allEntries = true)
    public Mono<Void> deleteConversation(Long conversationId, Long userId) {
        logger.info("用户 {} 正在尝试删除对话 {}", userId, conversationId);
        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("对话不存在，ID: " + conversationId)))
                .flatMap(conversation -> {
                    // 验证该对话是否属于当前用户
                    if (!conversation.getUserId().equals(userId)) {
                        logger.warn("安全警告：用户 {} 尝试删除不属于自己的对话 {}。", userId, conversationId);
                        return Mono.error(new SecurityException("无权删除此对话"));
                    }
                    // 先删除该对话下的所有消息，然后删除对话本身
                    return messageRepository.deleteByConversationId(conversationId)
                            .then(conversationRepository.deleteById(conversationId))
                            // 同步删除 ES 中的索引
                            .then(searchService.deleteConversationIndex(conversationId)
                                    .doOnError(e -> logger.error("删除对话索引失败，但不影响主流: ", e))
                                    .onErrorResume(e -> Mono.empty())
                            )
                            .doOnSuccess(v -> logger.info("对话 {} 及其所有消息已成功删除。", conversationId));
                })
                .then(); // 转换为 Mono<Void>
    }

    /**
     * 根据会话ID获取会话信息
     *
     * @param conversationId 会话ID
     * @return 会话对象
     * @throws IllegalArgumentException 当会话ID无效时抛出
     */
    @Override
    public Mono<Conversation> getConversationById(Long conversationId) {
        logger.info("开始获取会话信息，会话ID: {}", conversationId);

        return Mono.fromCallable(() -> {
            // 参数验证
            if (conversationId == null) {
                throw new IllegalArgumentException("会话ID不能为空");
            }

            if (conversationId <= 0) {
                throw new IllegalArgumentException("会话ID必须大于0");
            }

            return conversationId;
        })
        .flatMap(validatedId -> {
            logger.debug("查询会话信息，会话ID: {}", validatedId);
            return conversationRepository.findById(validatedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                    "会话不存在，ID: " + validatedId)));
        })
        .doOnSuccess(conversation -> {
            logger.info("会话信息获取成功，会话ID: {}, 标题: {}",
                       conversation.getId(), conversation.getTitle());
        })
        .doOnError(error -> {
            logger.error("获取会话信息失败，会话ID: {}, 错误: {}",
                       conversationId, error.getMessage(), error);
        });
    }
}
