package org.example.ai_agent.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AI 模型目录运行时资源。
 */
@Configuration
public class ModelCatalogConfig {

    @Bean(name = "aiStreamingExecutor", destroyMethod = "shutdown")
    @Primary
    public Executor aiStreamingExecutor(ModelCatalogProperties properties) {
        ModelCatalogProperties.Streaming streaming = properties.getStreaming();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-stream-");
        executor.setCorePoolSize(streaming.getCorePoolSize());
        executor.setMaxPoolSize(streaming.getMaxPoolSize());
        executor.setQueueCapacity(streaming.getQueueCapacity());
        executor.setKeepAliveSeconds(streaming.getKeepAliveSeconds());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
