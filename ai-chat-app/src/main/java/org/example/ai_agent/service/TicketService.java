package org.example.ai_agent.service;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Service
public class TicketService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private static final String TICKET_PREFIX = "ticket:";
    private static final Duration TICKET_EXPIRATION = Duration.ofSeconds(60);

    public TicketService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 为给定的用户名生成一次性票据并存储在Redis中。
     * @param username 已认证用户的用户名。
     * @return 包含生成票据的Mono。
     */
    public Mono<String> generateTicket(String username) {
        String ticket = UUID.randomUUID().toString();
        String redisKey = TICKET_PREFIX + ticket;
        return redisTemplate.opsForValue()
                .set(redisKey, username, TICKET_EXPIRATION)
                .thenReturn(ticket);
    }

    /**
     * 验证票据，获取关联的用户名，并消费该票据。
     * @param ticket 要验证的票据。
     * @return 如果票据有效则返回包含用户名的Mono，否则返回Mono.empty()。
     */
    public Mono<String> validateAndConsumeTicket(String ticket) {
        String redisKey = TICKET_PREFIX + ticket;
        // 使用getAndDelete原子性地"消费"票据
        // 这是确保密钥只使用一次的常见模式。
        // 我们获取旧值（用户名）并立即删除它。
        return redisTemplate.opsForValue().getAndDelete(redisKey);
    }
}
