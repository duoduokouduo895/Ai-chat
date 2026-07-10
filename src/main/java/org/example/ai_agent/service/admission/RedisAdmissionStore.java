package org.example.ai_agent.service.admission;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/** Redis 原子计数和租约操作，避免多实例下的 check-then-act 竞争。 */
@Component
public class RedisAdmissionStore {

    private static final DefaultRedisScript<Long> CONSUME_WITH_LIMIT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local limit = tonumber(ARGV[1])
            if current >= limit then
                return 0
            end
            current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> ACQUIRE_LEASE = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local leaseMillis = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            if redis.call('ZSCORE', KEYS[1], member) then
                redis.call('ZADD', KEYS[1], now + leaseMillis, member)
                redis.call('PEXPIRE', KEYS[1], leaseMillis + 60000)
                return 1
            end
            if redis.call('ZCARD', KEYS[1]) >= limit then
                return 0
            end
            redis.call('ZADD', KEYS[1], now + leaseMillis, member)
            redis.call('PEXPIRE', KEYS[1], leaseMillis + 60000)
            return 1
            """, Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisAdmissionStore(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> tryConsume(String key, int limit, Duration window) {
        return redisTemplate.execute(CONSUME_WITH_LIMIT, List.of(key), List.of(
                        Integer.toString(limit), Long.toString(window.toMillis())))
                .single(0L)
                .map(result -> result == 1L);
    }

    public Mono<Boolean> tryAcquireLease(String key, String member, int limit, Duration lease) {
        return redisTemplate.execute(ACQUIRE_LEASE, List.of(key), List.of(
                        Long.toString(System.currentTimeMillis()),
                        Long.toString(lease.toMillis()),
                        Integer.toString(limit),
                        member))
                .single(0L)
                .map(result -> result == 1L);
    }

    public Mono<Void> releaseLease(String key, String member) {
        return redisTemplate.opsForZSet().remove(key, member).then();
    }
}
