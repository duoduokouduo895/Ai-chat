package org.example.ai_agent.common.admission;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 对 AI 成本入口的服务端准入策略。
 *
 * <p>所有值都保留了代码级安全上限，避免配置中心误配置为无限制。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.security.admission")
public class ChatAdmissionProperties {

    private boolean enabled = true;
    private int globalConcurrentLimit = 20;
    private int registeredConcurrentLimit = 4;
    private Duration concurrencyLease = Duration.ofMinutes(2);
    private Guest guest = new Guest();
    private GuestCreation guestCreation = new GuestCreation();

    public int globalConcurrentLimit() {
        return bounded(globalConcurrentLimit, 1, 200);
    }

    public int registeredConcurrentLimit() {
        return bounded(registeredConcurrentLimit, 1, 20);
    }

    public Duration concurrencyLease() {
        Duration value = concurrencyLease == null ? Duration.ofMinutes(2) : concurrencyLease;
        if (value.compareTo(Duration.ofSeconds(30)) < 0) {
            return Duration.ofSeconds(30);
        }
        return value.compareTo(Duration.ofMinutes(10)) > 0 ? Duration.ofMinutes(10) : value;
    }

    private int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    @Data
    public static class Guest {
        private int requestLimit = 2;
        private Duration requestWindow = Duration.ofHours(24);
        private int concurrentLimit = 1;
        private boolean ragEnabled = false;
        /** Empty means every enabled catalog chat model is allowed. */
        private List<String> allowedModels = new ArrayList<>();

        public int requestLimit() {
            return Math.max(1, Math.min(requestLimit, 20));
        }

        public Duration requestWindow() {
            Duration value = requestWindow == null ? Duration.ofHours(24) : requestWindow;
            if (value.compareTo(Duration.ofMinutes(1)) < 0) {
                return Duration.ofMinutes(1);
            }
            return value.compareTo(Duration.ofDays(7)) > 0 ? Duration.ofDays(7) : value;
        }

        public int concurrentLimit() {
            return Math.max(1, Math.min(concurrentLimit, 3));
        }
    }

    @Data
    public static class GuestCreation {
        private int requestLimit = 5;
        private Duration requestWindow = Duration.ofHours(1);

        public int requestLimit() {
            return Math.max(1, Math.min(requestLimit, 30));
        }

        public Duration requestWindow() {
            Duration value = requestWindow == null ? Duration.ofHours(1) : requestWindow;
            if (value.compareTo(Duration.ofMinutes(1)) < 0) {
                return Duration.ofMinutes(1);
            }
            return value.compareTo(Duration.ofDays(1)) > 0 ? Duration.ofDays(1) : value;
        }
    }
}
