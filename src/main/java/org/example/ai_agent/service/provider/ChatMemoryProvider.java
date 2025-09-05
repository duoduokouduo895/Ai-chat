package org.example.ai_agent.service.provider;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.example.ai_agent.repository.chat.RedisChatMemeoryStore;
import org.springframework.stereotype.Service;

/**
 * 聊天记忆提供者服务
 * <p>
 * 负责为每个独立的会话提供一个隔离的、持久化的ChatMemory实例。
 */
@Service
public class ChatMemoryProvider {

    private final RedisChatMemeoryStore chatMemoryStore;

    /**
     * 使用构造函数注入RedisChatMemoryStore。
     *
     * @param chatMemoryStore Redis聊天记忆存储实例
     */
    public ChatMemoryProvider(RedisChatMemeoryStore chatMemoryStore) {
        this.chatMemoryStore = chatMemoryStore;
    }

    /**
     * 根据提供的记忆ID（通常是会话ID）获取或创建一个ChatMemory实例。
     *
     * @param memoryId 唯一的记忆ID，用于隔离不同会话的上下文。
     * @return 一个配置好的ChatMemory实例。
     */
    public ChatMemory get(String memoryId) {
        return MessageWindowChatMemory.builder()
                .id(memoryId) // 设置唯一的记忆ID
                .maxMessages(20) // 保留最近20条消息
                .chatMemoryStore(chatMemoryStore) // 关联Redis持久化存储
                .build();
    }
}
