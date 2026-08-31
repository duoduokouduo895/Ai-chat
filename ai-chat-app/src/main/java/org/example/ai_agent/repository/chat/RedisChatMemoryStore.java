package org.example.ai_agent.repository.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final Duration MEMORY_TTL = Duration.ofDays(1);
    private static final String CONVERSATION_MEMORY_INDEX_PREFIX = "chat-memory:conversation:";

    private final StringRedisTemplate redisTemplate;

    public RedisChatMemoryStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<ChatMessage> getMessages(Object conversationId) {
        String json = redisTemplate.opsForValue().get(conversationId.toString());
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object conversationId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(messages);
        String memoryId = conversationId.toString();
        redisTemplate.opsForValue().set(memoryId, json, MEMORY_TTL);
        Optional<String> indexKey = conversationMemoryIndexKey(memoryId);
        if (indexKey.isPresent()) {
            redisTemplate.opsForSet().add(indexKey.get(), memoryId);
            redisTemplate.expire(indexKey.get(), MEMORY_TTL);
        }
    }

    @Override
    public void deleteMessages(Object conversationId) {
        redisTemplate.delete(conversationId.toString());
    }

    public void deleteConversationMessages(Long conversationId) {
        if (conversationId == null) {
            return;
        }

        String indexKey = CONVERSATION_MEMORY_INDEX_PREFIX + conversationId;
        Set<String> memoryKeys = new HashSet<>();
        SetOperations<String, String> setOperations = redisTemplate.opsForSet();
        Set<String> indexedKeys = setOperations.members(indexKey);
        if (indexedKeys != null) {
            memoryKeys.addAll(indexedKeys);
        }

        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(conversationId + ":*")
                .count(100)
                .build();
        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                memoryKeys.add(cursor.next());
            }
        }

        if (!memoryKeys.isEmpty()) {
            redisTemplate.delete(memoryKeys);
        }
        redisTemplate.delete(indexKey);
    }

    private Optional<String> conversationMemoryIndexKey(String memoryId) {
        int separatorIndex = memoryId.indexOf(':');
        if (separatorIndex <= 0) {
            return Optional.empty();
        }
        return Optional.of(CONVERSATION_MEMORY_INDEX_PREFIX + memoryId.substring(0, separatorIndex));
    }
}
