package org.example.ai_agent.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class HealthCheckTask {

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${health-check.enabled:false}")
    private boolean healthCheckEnabled;

    private final WebClient webClient;

    public HealthCheckTask() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    @Scheduled(cron = "${health-check.cron:0 */10 * * * ?}")
    public void performHealthCheck() {
        if (!healthCheckEnabled) {
            log.debug("健康检查已关闭: health-check.enabled=false");
            return;
        }

        checkActuatorHealth();
        checkApplicationStatus();
    }

    private void checkActuatorHealth() {
        String actuatorUrl = "http://localhost:" + serverPort + "/actuator/health";

        webClient.get()
                .uri(actuatorUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .doOnSuccess(response -> {
                    String status = (String) response.get("status");
                    if ("UP".equals(status)) {
                        log.info("定时健康检查: Actuator=UP");
                    } else {
                        log.warn("定时健康检查: Actuator={}, detail={}", status, response);
                    }
                })
                .onErrorResume(error -> {
                    log.warn("定时健康检查失败: {}", error.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    private void checkApplicationStatus() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsagePercent = ((double) usedMemory / maxMemory) * 100;
        int activeThreads = Thread.activeCount();

        if (memoryUsagePercent > 80) {
            log.warn("JVM 内存使用率较高: {}%", String.format("%.2f", memoryUsagePercent));
        }
        if (activeThreads > 100) {
            log.warn("活跃线程数较多: {}", activeThreads);
        }

        log.debug("应用状态: usedMemory={}MB, maxMemory={}MB, memoryUsage={}%, activeThreads={}",
                usedMemory / 1024 / 1024,
                maxMemory / 1024 / 1024,
                String.format("%.2f", memoryUsagePercent),
                activeThreads);
    }

    public void manualHealthCheck() {
        performHealthCheck();
    }
}
