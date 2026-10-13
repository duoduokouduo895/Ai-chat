package org.example.ai_agent.admin.operations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Optional AIDiag integration. Invalid or missing config disables only the operations BFF. */
@Component
public class AidiagIntegrationSettings {

    private static final Pattern SERVICE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final boolean available;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final String serviceName;
    private final Duration timeout;

    public AidiagIntegrationSettings(@Value("${aidiag.integration.enabled:false}") boolean enabled,
            @Value("${aidiag.integration.base-url:http://aidiag-backend:8080}") String baseUrl,
            @Value("${aidiag.integration.username:aidiag-reader}") String username,
            @Value("${aidiag.integration.secret-file:/run/secrets/aidiag.read-api.secret}") String secretFile,
            @Value("${aidiag.integration.service-name:ai-chat-candidate}") String serviceName,
            @Value("${aidiag.integration.timeout:3s}") Duration timeout) {
        var normalizedUrl = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        var normalizedUsername = username == null ? "" : username.strip();
        var normalizedServiceName = serviceName == null ? "" : serviceName.strip();
        var secret = readSecret(secretFile);
        var valid = enabled && (normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://"))
                && !normalizedUsername.isEmpty() && SERVICE_NAME.matcher(normalizedServiceName).matches()
                && secret.length() >= 32 && !timeout.isNegative() && !timeout.isZero() && timeout.compareTo(Duration.ofSeconds(10)) <= 0;
        this.available = valid;
        this.baseUrl = valid ? normalizedUrl : "http://127.0.0.1";
        this.username = normalizedUsername;
        this.password = secret;
        this.serviceName = normalizedServiceName;
        this.timeout = valid ? timeout : Duration.ofSeconds(3);
    }

    private static String readSecret(String secretFile) {
        if (secretFile == null || secretFile.isBlank()) {
            return "";
        }
        try {
            return Files.readString(Path.of(secretFile)).strip();
        } catch (Exception exception) {
            return "";
        }
    }

    public boolean available() { return available; }
    public String baseUrl() { return baseUrl; }
    public String username() { return username; }
    public String password() { return password; }
    public String serviceName() { return serviceName; }
    public Duration timeout() { return timeout; }
}
