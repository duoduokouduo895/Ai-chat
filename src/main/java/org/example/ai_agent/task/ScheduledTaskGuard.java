package org.example.ai_agent.task;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 防止同一实例内的定时任务重叠执行。
 */
final class ScheduledTaskGuard {

    private final AtomicBoolean running = new AtomicBoolean(false);

    boolean tryStart() {
        return running.compareAndSet(false, true);
    }

    void finish() {
        running.set(false);
    }
}
