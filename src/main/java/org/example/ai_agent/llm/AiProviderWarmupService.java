package org.example.ai_agent.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class AiProviderWarmupService {

    private final ModelCatalogProperties properties;

    public AiProviderWarmupService(ModelCatalogProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        CompletableFuture.runAsync(this::doWarmup);
    }

    private void doWarmup() {
        ModelCatalogProperties.Reliability reliability = properties.getReliability();
        if (reliability == null || !reliability.isWarmupEnabled()) {
            return;
        }

        String host = StringUtils.hasText(reliability.getWarmupHost())
                ? reliability.getWarmupHost().trim()
                : hostFromBaseUrl(properties.getBaseUrl());
        if (!StringUtils.hasText(host)) {
            log.warn("AI供应商预热跳过: 未配置 base-url 或 warmup-host");
            return;
        }

        try {
            long start = System.nanoTime();
            InetAddress[] addresses = InetAddress.getAllByName(host);
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.info("AI供应商 DNS 预热完成: host={}, addressCount={}, duration={}ms",
                    host, addresses.length, durationMs);
        } catch (Exception e) {
            log.warn("AI供应商 DNS 预热失败: host={}, errorType={}", host, e.getClass().getSimpleName());
        }
    }

    private String hostFromBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        try {
            return URI.create(baseUrl.trim()).getHost();
        } catch (Exception ignored) {
            return null;
        }
    }
}
