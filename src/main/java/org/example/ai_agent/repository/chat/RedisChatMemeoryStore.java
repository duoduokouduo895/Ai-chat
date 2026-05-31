package org.example.ai_agent.repository.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class RedisChatMemeoryStore implements ChatMemoryStore {

    private static final Duration MEMORY_TTL = Duration.ofDays(1);
    private static final String CONVERSATION_MEMORY_INDEX_PREFIX = "chat-memory:conversation:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public List<ChatMessage> getMessages(Object conversationid) {
        //获取对话信息
        String json = redisTemplate.opsForValue().get(conversationid.toString());
        //转换
        List<ChatMessage> list = ChatMessageDeserializer.messagesFromJson(json);
        return list;
    }

    @Override
    public void updateMessages(Object conversationid, List<ChatMessage> list) {
        //更新会话消息
        String Json = ChatMessageSerializer.messagesToJson(list);
        //存入redis
        String memoryId = conversationid.toString();
        redisTemplate.opsForValue().set(memoryId, Json, MEMORY_TTL);
        String indexKey = conversationMemoryIndexKey(memoryId);
        if (indexKey != null) {
            redisTemplate.opsForSet().add(indexKey, memoryId);
            redisTemplate.expire(indexKey, MEMORY_TTL);
        }
    }

    @Override
    public void deleteMessages(Object conversationid) {
            redisTemplate.delete(conversationid.toString());
    }

    public void deleteConversationMessages(Long conversationId) {
        if (conversationId == null) {
            return;
        }

        String indexKey = CONVERSATION_MEMORY_INDEX_PREFIX + conversationId;
        Set<String> memoryKeys = new HashSet<>();
        Set<String> indexedKeys = redisTemplate.opsForSet().members(indexKey);
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

    private String conversationMemoryIndexKey(String memoryId) {
        int separatorIndex = memoryId.indexOf(':');
        if (separatorIndex <= 0) {
            return null;
        }
        return CONVERSATION_MEMORY_INDEX_PREFIX + memoryId.substring(0, separatorIndex);
    }
}
