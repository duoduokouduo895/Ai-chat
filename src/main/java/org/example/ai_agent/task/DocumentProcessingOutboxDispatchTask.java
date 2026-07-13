package org.example.ai_agent.task;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.service.impl.document.DocumentProcessingOutboxService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期投递已持久化但尚未获得 broker 确认的文档处理消息。 */
@Slf4j
@Component
public class DocumentProcessingOutboxDispatchTask {

    private final DocumentProcessingOutboxService outboxService;
    private final ScheduledTaskGuard taskGuard = new ScheduledTaskGuard();

    public DocumentProcessingOutboxDispatchTask(DocumentProcessingOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelayString = "${knowledge-base.processing.outbox-dispatch-delay-ms:5000}")
    public void dispatchPendingEvents() {
        if (!taskGuard.tryStart()) {
            log.debug("文档 outbox 上一轮投递尚未结束，跳过本轮");
            return;
        }

        outboxService.dispatchPending()
                .doOnError(error -> log.error("文档 outbox 投递轮次失败: errorType={}",
                        error.getClass().getSimpleName()))
                .doFinally(signal -> taskGuard.finish())
                .subscribe();
    }
}
