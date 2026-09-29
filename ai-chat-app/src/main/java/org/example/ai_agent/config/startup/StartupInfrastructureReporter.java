package org.example.ai_agent.config.startup;

import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.database.ElasticsearchConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "startup.infrastructure-report", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StartupInfrastructureReporter {

    private static final String PROBE_KEY = "ai-agent:startup:probe";

    private final Environment environment;
    private final ObjectProvider<ConnectionFactory> connectionFactoryProvider;
    private final ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final ElasticsearchConfig elasticsearchConfig;

    @Value("${startup.infrastructure-report.timeout-seconds:5}")
    private int timeoutSeconds;

    @Value("${startup.infrastructure-report.mysql-warmup-connections:${spring.r2dbc.pool.initial-size:2}}")
    private int mysqlWarmupConnections;

    public StartupInfrastructureReporter(
            Environment environment,
            ObjectProvider<ConnectionFactory> connectionFactoryProvider,
            ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider,
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
            ElasticsearchConfig elasticsearchConfig
    ) {
        this.environment = environment;
        this.connectionFactoryProvider = connectionFactoryProvider;
        this.redisTemplateProvider = redisTemplateProvider;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
        this.elasticsearchConfig = elasticsearchConfig;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reportAfterApplicationReady() {
        log.info("启动组件摘要开始");
        reportExternalConfig();

        Map<String, CompletableFuture<CheckResult>> checks = new LinkedHashMap<>();
        checks.put("MySQL", CompletableFuture.supplyAsync(this::checkMysql));
        checks.put("Redis", CompletableFuture.supplyAsync(this::checkRedis));
        checks.put("RabbitMQ", CompletableFuture.supplyAsync(this::checkRabbitMq));
        checks.put("Elasticsearch", CompletableFuture.supplyAsync(this::checkElasticsearch));

        CompletableFuture<Void> allChecks = CompletableFuture.allOf(
                checks.values().toArray(CompletableFuture[]::new)
        );

        try {
            allChecks.get(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("启动组件摘要等待超时或异常，将输出已完成的检查结果: {}", simpleError(e));
        }

        checks.forEach((component, future) -> {
            CheckResult result = future.getNow(CheckResult.down("检查未在总超时内完成"));
            report(component, result);
        });
        log.info("启动组件摘要完成");
    }

    private void reportExternalConfig() {
        List<String> configFiles = propertySourceNames(name -> name.contains("'file ["));
        List<String> secretTrees = propertySourceNames(name -> name.startsWith("Config tree"));
        List<String> missingKeys = missingImportantConfigKeys();

        String detail = "files=" + configFiles.size()
                + ", secretTrees=" + secretTrees.size()
                + ", keyConfig=" + (missingKeys.isEmpty() ? "齐全" : "缺失" + missingKeys);

        if (configFiles.isEmpty() || secretTrees.isEmpty() || !missingKeys.isEmpty()) {
            log.warn("组件状态 | ExternalConfig=WARN | {}，请检查 AICHAT_CONFIG_DIR / AICHAT_CONFIG_SECRETS_DIR", detail);
            return;
        }

        log.info("组件状态 | ExternalConfig=UP | {}", detail);
        log.debug("External PropertySource: files={}, secretTrees={}", configFiles, secretTrees);
    }

    private CheckResult checkMysql() {
        long startNanos = System.nanoTime();
        ConnectionFactory connectionFactory = connectionFactoryProvider.getIfAvailable();
        if (connectionFactory == null) {
            return CheckResult.down("ConnectionFactory Bean 未创建", elapsedMs(startNanos));
        }

        try {
            int warmupConnections = Math.max(1, mysqlWarmupConnections);
            Integer warmedConnections = Flux.range(1, warmupConnections)
                    .flatMap(ignored -> Mono.from(connectionFactory.create())
                            .flatMap(connection -> Mono.from(connection.close()).thenReturn(1)), warmupConnections)
                    .reduce(0, Integer::sum)
                    .timeout(timeout())
                    .block();

            if (warmedConnections != null && warmedConnections > 0) {
                return CheckResult.up("endpoint=" + mysqlEndpoint()
                        + ", warmupConnections=" + warmedConnections, elapsedMs(startNanos));
            }
            return CheckResult.down("连接创建成功但关闭结果未知", elapsedMs(startNanos));
        } catch (Exception e) {
            return CheckResult.down(simpleError(e), elapsedMs(startNanos));
        }
    }

    private CheckResult checkRedis() {
        long startNanos = System.nanoTime();
        ReactiveStringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return CheckResult.down("ReactiveStringRedisTemplate Bean 未创建", elapsedMs(startNanos));
        }

        try {
            redisTemplate.hasKey(PROBE_KEY)
                    .timeout(timeout())
                    .block();
            return CheckResult.up("endpoint=" + redisEndpoint(), elapsedMs(startNanos));
        } catch (Exception e) {
            return CheckResult.down(simpleError(e), elapsedMs(startNanos));
        }
    }

    private CheckResult checkRabbitMq() {
        long startNanos = System.nanoTime();
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate == null) {
            return CheckResult.down("RabbitTemplate Bean 未创建", elapsedMs(startNanos));
        }

        try {
            Boolean connected = CompletableFuture
                    .supplyAsync(() -> rabbitTemplate.execute(channel -> channel.isOpen()))
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(connected)) {
                return CheckResult.up("endpoint=" + rabbitMqEndpoint(), elapsedMs(startNanos));
            }
            return CheckResult.down("Channel 未打开", elapsedMs(startNanos));
        } catch (Exception e) {
            return CheckResult.down(simpleError(e), elapsedMs(startNanos));
        }
    }

    private CheckResult checkElasticsearch() {
        long startNanos = System.nanoTime();
        String uri = elasticsearchConfig.getElasticsearchUri();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(timeout())
                    .build();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(timeout())
                    .GET();
            if (elasticsearchConfig.hasCredentials()) {
                String encoded = Base64.getEncoder().encodeToString(
                        (elasticsearchConfig.getUsername() + ":" + elasticsearchConfig.getPassword())
                                .getBytes(StandardCharsets.UTF_8)
                );
                requestBuilder.header("Authorization", "Basic " + encoded);
            }
            HttpRequest request = requestBuilder.build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return CheckResult.up("endpoint=" + safeUri(uri) + ", status=" + statusCode,
                        elapsedMs(startNanos));
            }
            return CheckResult.down("endpoint=" + safeUri(uri) + ", status=" + statusCode,
                    elapsedMs(startNanos));
        } catch (Exception e) {
            return CheckResult.down(simpleError(e), elapsedMs(startNanos));
        }
    }

    private void report(String component, CheckResult result) {
        if (result.up()) {
            log.info("组件状态 | {}=UP | durationMs={} | {}", component, result.durationMs(), result.detail());
        } else {
            log.warn("组件状态 | {}=DOWN | durationMs={} | {}", component, result.durationMs(), result.detail());
        }
    }

    private List<String> propertySourceNames(Predicate<String> filter) {
        List<String> names = new ArrayList<>();
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return names;
        }

        for (PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
            String name = propertySource.getName();
            if (name != null && filter.test(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private List<String> missingImportantConfigKeys() {
        List<String> missingKeys = new ArrayList<>();
        requireAny(missingKeys, "MySQL", "spring.r2dbc.host", "spring.r2dbc.url");
        requireAny(missingKeys, "Redis", "spring.data.redis.host", "spring.redis.host");
        requireAny(missingKeys, "RabbitMQ", "spring.rabbitmq.host");
        requireAny(missingKeys, "Elasticsearch", "spring.elasticsearch.uris");
        requireAny(missingKeys, "JWT", "jwt.secret");
        return missingKeys;
    }

    private void requireAny(List<String> missingKeys, String name, String... keys) {
        boolean exists = Arrays.stream(keys).anyMatch(key -> environment.getProperty(key) != null);
        if (!exists) {
            missingKeys.add(name);
        }
    }

    private String mysqlEndpoint() {
        String host = property("spring.r2dbc.host", "unknown");
        String port = property("spring.r2dbc.port", "3306");
        String database = property("spring.r2dbc.database", "unknown");
        return host + ":" + port + "/" + database;
    }

    private String redisEndpoint() {
        String host = firstProperty("unknown", "spring.data.redis.host", "spring.redis.host");
        String port = firstProperty("6379", "spring.data.redis.port", "spring.redis.port");
        return host + ":" + port;
    }

    private String rabbitMqEndpoint() {
        String host = property("spring.rabbitmq.host", "unknown");
        String port = property("spring.rabbitmq.port", "5672");
        String virtualHost = property("spring.rabbitmq.virtual-host", "/");
        return host + ":" + port + ", vhost=" + virtualHost;
    }

    private String safeUri(String rawUri) {
        try {
            URI uri = URI.create(rawUri);
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
            int port = uri.getPort();
            String portPart = port >= 0 ? ":" + port : "";
            String path = uri.getPath() == null ? "" : uri.getPath();
            return scheme + "://" + uri.getHost() + portPart + path;
        } catch (Exception e) {
            return rawUri;
        }
    }

    private String property(String key, String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }

    private String firstProperty(String defaultValue, String... keys) {
        return Arrays.stream(keys)
                .map(environment::getProperty)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(defaultValue);
    }

    private Duration timeout() {
        return Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String simpleError(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private record CheckResult(boolean up, String detail, long durationMs) {
        static CheckResult up(String detail, long durationMs) {
            return new CheckResult(true, detail, durationMs);
        }

        static CheckResult down(String detail, long durationMs) {
            return new CheckResult(false, detail, durationMs);
        }

        static CheckResult down(String detail) {
            return new CheckResult(false, detail, -1);
        }
    }
}
