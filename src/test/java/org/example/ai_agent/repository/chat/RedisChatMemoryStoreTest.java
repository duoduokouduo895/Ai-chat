package org.example.ai_agent.repository.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisChatMemoryStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private SetOperations<String, String> setOperations;
    @Mock
    private Cursor<String> cursor;

    @Test
    void deleteConversationMessagesUsesScanAndIndexedKeys() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("chat-memory:conversation:42"))
                .thenReturn(Set.of("42:model-a"));
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("42:model-b");

        RedisChatMemoryStore store = new RedisChatMemoryStore(redisTemplate);

        store.deleteConversationMessages(42L);

        verify(redisTemplate).scan(any(ScanOptions.class));
        verify(redisTemplate).delete(org.mockito.ArgumentMatchers.<java.util.Collection<String>>argThat(keys ->
                keys instanceof Set<?> keySet
                        && keySet.containsAll(List.of("42:model-a", "42:model-b"))));
        verify(redisTemplate).delete("chat-memory:conversation:42");
    }
}
