package org.example.ai_agent.scheduler;

import org.example.ai_agent.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Elasticsearch 同步调度器
 * 根据 application.properties 中的配置，定时将数据库中的数据同步到 Elasticsearch。
 */
@Component
public class ElasticsearchSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchSyncScheduler.class);

    private final SearchService searchService;

    @Value("${elasticsearch.sync.enabled:true}")
    private boolean syncEnabled;

    public ElasticsearchSyncScheduler(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 定时执行索引重建任务。
     * cron 表达式从 application.properties 文件中读取。
     * 只有在 elasticsearch.sync.enabled=true 时才会执行。
     */
    @Scheduled(cron = "${elasticsearch.sync.cron}")
    public void scheduleIndexRebuild() {
        logger.info("Elasticsearch 同步任务调度器已触发。");

        if (syncEnabled) {
            logger.info("同步功能已启用，开始执行索引重建...");

            // 添加超时控制和更好的错误处理
            searchService.rebuildAllIndexes()
                .timeout(java.time.Duration.ofMinutes(10)) // 10分钟超时
                .doOnSuccess(v -> logger.info("Elasticsearch 索引重建任务成功完成。"))
                .doOnError(error -> logger.error("Elasticsearch 索引重建任务执行失败。", error))
                .onErrorResume(error -> {
                    // 失败时记录错误但不中断调度器
                    logger.warn("🚨 ES索引重建失败，将在下次调度时重试: {}", error.getMessage());
                    return Mono.empty();
                })
                .subscribe(); // 在响应式编程中，必须订阅才会触发执行
        } else {
            logger.info("同步功能已禁用 (elasticsearch.sync.enabled=false)，跳过本次任务。");
        }
    }
}
