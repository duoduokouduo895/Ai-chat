package org.example.ai_agent.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 自动健康检查定时任务
 * 每10分钟自动执行一次健康检查，并将结果记录到日志中
 */
@Component
public class HealthCheckTask {
    
    private static final Logger log = LoggerFactory.getLogger(HealthCheckTask.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Value("${server.port:8080}")
    private int serverPort;
    
    @Value("${health-check.enabled:true}")
    private boolean healthCheckEnabled;
    
    @Value("${health-check.log-level:INFO}")
    private String logLevel;
    
    private final WebClient webClient;
    
    public HealthCheckTask() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024)) // 1MB
                .build();
    }
    

    @Scheduled(cron = "0 */10 * * * ?")
    public void performHealthCheck() {
        if (!healthCheckEnabled) {
            log.debug("健康检查功能已禁用 (health-check.enabled=false)，跳过本次检查。");
            return;
        }
        
        String timestamp = LocalDateTime.now().format(formatter);
        log.info("========================================");
        log.info("自动健康检查开始 - {}", timestamp);
        log.info("========================================");
        
        try {
            // 1. 检查 Actuator 健康端点
            checkActuatorHealth();
            
            // 2. 检查自定义诊断端点
            checkDiagnosticHealth();
            
            // 3. 检查应用基本状态
            checkApplicationStatus();
            
        } catch (Exception e) {
            log.error("健康检查执行失败", e);
        }
        
        log.info("========================================");
        log.info("自动健康检查完成 - {}", timestamp);
        log.info("========================================");
    }
    
    /**
     * 检查 Spring Boot Actuator 健康端点
     */
    private void checkActuatorHealth() {
        String actuatorUrl = "http://localhost:" + serverPort + "/actuator/health";
        
        webClient.get()
                .uri(actuatorUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(java.time.Duration.ofSeconds(10))
                .doOnSuccess(response -> {
                    String status = (String) response.get("status");
                    logHealthResult("Actuator健康检查", status, response);
                })
                .doOnError(error -> {
                    log.error("Actuator健康检查失败: {}", error.getMessage());
                })
                .onErrorResume(error -> {
                    // 记录错误但不中断其他检查
                    log.warn("❌ Actuator健康检查超时或失败，跳过此检查");
                    return Mono.empty();
                })
                .block(java.time.Duration.ofSeconds(12)); // 等待结果，但不超过12秒
    }
    
    /**
     * 检查自定义诊断端点
     */
    private void checkDiagnosticHealth() {
        String diagnosticUrl = "http://localhost:" + serverPort + "/api/diagnostic/health";
        
        webClient.get()
                .uri(diagnosticUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(java.time.Duration.ofSeconds(15))
                .doOnSuccess(response -> {
                    String status = (String) response.get("status");
                    Object totalDocs = response.get("totalDocuments");
                    logDiagnosticResult("自定义诊断检查", status, totalDocs, response);
                })
                .doOnError(error -> {
                    log.error("自定义诊断检查失败: {}", error.getMessage());
                })
                .onErrorResume(error -> {
                    // 记录错误但不中断其他检查
                    log.warn("❌ 自定义诊断检查超时或失败，跳过此检查");
                    return Mono.empty();
                })
                .block(java.time.Duration.ofSeconds(17)); // 等待结果，但不超过17秒
    }
    
    /**
     * 检查应用基本状态
     */
    private void checkApplicationStatus() {
        try {
            // 检查 JVM 内存使用情况
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();
            
            double memoryUsagePercent = ((double) usedMemory / maxMemory) * 100;
            
            log.info("📊 JVM内存状态:");
            log.info("   已使用内存: {} MB", usedMemory / 1024 / 1024);
            log.info("   总分配内存: {} MB", totalMemory / 1024 / 1024);
            log.info("   最大可用内存: {} MB", maxMemory / 1024 / 1024);
            log.info("   内存使用率: {:.2f}%", memoryUsagePercent);
            
            if (memoryUsagePercent > 80) {
                log.warn("⚠️ 内存使用率较高: {:.2f}%", memoryUsagePercent);
            }
            
            // 检查活跃线程数
            int activeThreads = Thread.activeCount();
            log.info("🧵 活跃线程数: {}", activeThreads);
            
            if (activeThreads > 100) {
                log.warn("⚠️ 活跃线程数较多: {}", activeThreads);
            }
            
        } catch (Exception e) {
            log.error("应用状态检查失败", e);
        }
    }
    
    /**
     * 记录健康检查结果
     */
    private void logHealthResult(String checkType, String status, Map<String, Object> response) {
        if ("UP".equals(status)) {
            log.info("✅ {} - 状态: {}", checkType, status);
            
            // 如果是详细日志级别，显示更多信息
            if ("DEBUG".equalsIgnoreCase(logLevel)) {
                log.debug("   详细信息: {}", response);
            }
        } else {
            log.warn("❌ {} - 状态: {}", checkType, status);
            log.warn("   详细信息: {}", response);
        }
    }
    
    /**
     * 记录诊断检查结果
     */
    private void logDiagnosticResult(String checkType, String status, Object totalDocs, Map<String, Object> response) {
        if ("OK".equals(status)) {
            log.info("✅ {} - 状态: {}", checkType, status);
            if (totalDocs != null) {
                log.info("   文档总数: {}", totalDocs);
            }
            
            // 处理统计信息
            Object stats = response.get("processingStatistics");
            if (stats instanceof Map) {
                Map<String, Object> statsMap = (Map<String, Object>) stats;
                log.info("   处理统计: {}", statsMap);
            }
            
            if ("DEBUG".equalsIgnoreCase(logLevel)) {
                log.debug("   完整响应: {}", response);
            }
        } else {
            log.warn("❌ {} - 状态: {}", checkType, status);
            log.warn("   详细信息: {}", response);
        }
    }
    
    /**
     * 手动触发健康检查（用于测试）
     */
    public void manualHealthCheck() {
        log.info("手动触发健康检查");
        performHealthCheck();
    }
}