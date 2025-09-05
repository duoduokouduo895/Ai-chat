package org.example.ai_agent.task;

import org.example.ai_agent.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 游客用户清理定时任务
 * 每天凌晨2点自动清理创建时间超过1天的游客用户
 */
@Component
public class GuestUserCleanupTask {
    
    private static final Logger log = LoggerFactory.getLogger(GuestUserCleanupTask.class);
    
    private final UserService userService;
    
    public GuestUserCleanupTask(UserService userService) {
        this.userService = userService;
    }
    
    /**
     * 定时清理过期游客用户
     * 每天凌晨2点执行（cron表达式：秒 分 时 日 月 周）
     * 0 0 2 * * ? 表示每天凌晨2点0分0秒执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredGuestUsers() {
        log.info("定时任务开始：清理过期游客用户");
        
        userService.cleanupExpiredGuestUsers()
                .doOnSuccess(deletedCount -> {
                    log.info("定时任务完成：成功清理了 {} 个过期游客用户", deletedCount);
                })
                .doOnError(error -> {
                    log.error("定时任务执行失败：清理过期游客用户时发生错误", error);
                })
                .subscribe(); // 订阅以触发执行
    }
    
    /**
     * 手动触发清理任务（用于测试）
     * 每5分钟执行一次，仅用于开发和测试环境
     * 生产环境应该注释掉这个方法
     */
    // @Scheduled(fixedRate = 300000) // 5分钟 = 300000毫秒
    public void manualCleanupForTesting() {
        log.info("手动测试任务开始：清理过期游客用户");
        
        userService.cleanupExpiredGuestUsers()
                .doOnSuccess(deletedCount -> {
                    log.info("手动测试任务完成：成功清理了 {} 个过期游客用户", deletedCount);
                })
                .doOnError(error -> {
                    log.error("手动测试任务执行失败：清理过期游客用户时发生错误", error);
                })
                .subscribe();
    }
}