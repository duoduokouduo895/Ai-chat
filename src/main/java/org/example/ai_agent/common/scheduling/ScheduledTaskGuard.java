package org.example.ai_agent.common.scheduling;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 防止同一实例内的定时任务重叠执行。
 */
public final class ScheduledTaskGuard {

    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean tryStart() {
        return running.compareAndSet(false, true);
    }

    public void finish() {
        running.set(false);
    }
}
