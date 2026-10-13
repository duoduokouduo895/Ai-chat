package org.example.ai_agent.document.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** 将阻塞式 Tika 解析与通用 boundedElastic 调度器隔离。 */
@Configuration
public class DocumentParsingSchedulerConfig {

    @Bean(name = "documentParsingScheduler", destroyMethod = "dispose")
    public Scheduler documentParsingScheduler(DocumentProcessingProperties properties) {
        return Schedulers.newBoundedElastic(
                properties.effectiveParserMaxThreads(),
                properties.effectiveParserQueueCapacity(),
                "document-parse");
    }
}
