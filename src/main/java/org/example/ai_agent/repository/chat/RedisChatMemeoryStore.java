package org.example.ai_agent.repository.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RedisChatMemeoryStore implements ChatMemoryStore {

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
        redisTemplate.opsForValue().set(conversationid.toString(),Json, java.time.Duration.ofDays(1));
    }

    @Override
    public void deleteMessages(Object conversationid) {
            redisTemplate.delete(conversationid.toString());
    }
}
