package org.example.ai_agent.config.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis缓存配置类
 * 配置不同类型数据的缓存策略和过期时间
 */
@Configuration
@EnableCaching
@ConfigurationProperties(prefix = "cache")
public class CacheConfig {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 配置Redis缓存管理器
     * @param redisConnectionFactory Redis连接工厂
     * @return 缓存管理器
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // 默认缓存配置，使用配置好的ObjectMapper
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30)) // 默认30分钟过期
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues(); // 不缓存null值

        // 针对不同缓存区域的特定配置
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // 对话列表缓存 - 2分钟过期（优化：根据指南调整缓存时间）
        cacheConfigurations.put("conversations", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        
        // 模型列表缓存 - 1小时过期（模型列表变化较少）
        cacheConfigurations.put("models", defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // 搜索结果缓存 - 5分钟过期（优化：减少缓存时间）
        cacheConfigurations.put("searchResults", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // 搜索统计缓存 - 10分钟过期（优化：减少缓存时间）
        cacheConfigurations.put("searchStats", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        
        // 热门搜索缓存 - 15分钟过期（优化：减少缓存时间）
        cacheConfigurations.put("popularSearches", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        
        // 用户搜索历史缓存 - 30分钟过期（优化：减少缓存时间）
        cacheConfigurations.put("searchHistory", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // 对话消息缓存 - 15分钟过期（优化：大幅减少缓存时间提高数据一致性）
        cacheConfigurations.put("conversationMessages", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        
        // 限流缓存 - 1小时过期
        cacheConfigurations.put("rateLimits", defaultConfig.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * 缓存区域常量
     */
    public static class CacheNames {
        public static final String CONVERSATIONS = "conversations";
        public static final String MODELS = "models";
        public static final String SEARCH_RESULTS = "searchResults";
        public static final String SEARCH_STATS = "searchStats";
        public static final String POPULAR_SEARCHES = "popularSearches";
        public static final String SEARCH_HISTORY = "searchHistory";
        public static final String CONVERSATION_MESSAGES = "conversationMessages";
    }

    // 对话历史缓存配置
    private Conversation conversation = new Conversation();
    
    // AI响应缓存配置
    private AiResponse aiResponse = new AiResponse();

    /**
     * 获取对话历史过期时间（小时）
     * @return 过期时间
     */
    public int getConversationHistoryExpirationHours() {
        return conversation.getExpireHours();
    }

    /**
     * 获取AI响应缓存过期时间（天）
     * @return 过期时间
     */
    public int getAiResponseExpirationDays() {
        return aiResponse.getExpireDays();
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public AiResponse getAiResponse() {
        return aiResponse;
    }

    public void setAiResponse(AiResponse aiResponse) {
        this.aiResponse = aiResponse;
    }

    /**
     * 对话缓存配置
     */
    public static class Conversation {
        private int expireHours = 1; // 优化：从24小时减少到1小时
        private int maxSize = 1000;
        private boolean enableCompression = true;

        public int getExpireHours() {
            return expireHours;
        }

        public void setExpireHours(int expireHours) {
            this.expireHours = expireHours;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public boolean isEnableCompression() {
            return enableCompression;
        }

        public void setEnableCompression(boolean enableCompression) {
            this.enableCompression = enableCompression;
        }
    }

    /**
     * AI响应缓存配置
     */
    public static class AiResponse {
        private int expireDays = 1; // 优化：从7天减少到1天
        private int maxSize = 5000;
        private boolean enableCompression = true;
        private boolean cacheNullValues = false;

        public int getExpireDays() {
            return expireDays;
        }

        public void setExpireDays(int expireDays) {
            this.expireDays = expireDays;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public boolean isEnableCompression() {
            return enableCompression;
        }

        public void setEnableCompression(boolean enableCompression) {
            this.enableCompression = enableCompression;
        }

        public boolean isCacheNullValues() {
            return cacheNullValues;
        }

        public void setCacheNullValues(boolean cacheNullValues) {
            this.cacheNullValues = cacheNullValues;
        }
    }
}