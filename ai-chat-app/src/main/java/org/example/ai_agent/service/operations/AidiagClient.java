package org.example.ai_agent.service.operations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelOption;
import org.example.ai_agent.config.operations.AidiagIntegrationSettings;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/** Bounded server-side client. The browser never receives the AIDiag credential. */
@Component
public class AidiagClient {

    private final AidiagIntegrationSettings settings;
    private final WebClient webClient;

    public AidiagClient(AidiagIntegrationSettings settings) {
        this.settings = settings;
        var httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) Math.min(settings.timeout().toMillis(), 10_000))
                .responseTimeout(settings.timeout());
        this.webClient = WebClient.builder().baseUrl(settings.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(512 * 1024))
                .defaultHeaders(headers -> headers.setBasicAuth(settings.username(), settings.password())).build();
    }

    public Mono<IncidentPage> incidents(int page, int size, String status, String severity) {
        return request(IncidentPage.class, uriBuilder -> {
            var builder = uriBuilder.path("/api/v1/incidents").queryParam("page", page).queryParam("size", size)
                    .queryParam("service", settings.serviceName());
            if (status != null && !status.isBlank()) builder.queryParam("status", status);
            if (severity != null && !severity.isBlank()) builder.queryParam("severity", severity);
            return builder.build();
        });
    }

    public Mono<ShadowSummary> summary(int windowHours) {
        return request(ShadowSummary.class, builder -> builder.path("/api/v1/incidents/shadow-summary")
                .queryParam("service", settings.serviceName()).queryParam("windowHours", windowHours).build());
    }

    public Mono<IncidentDetail> detail(UUID incidentId) {
        return request(IncidentDetail.class, builder -> builder.path("/api/v1/incidents/{id}").build(incidentId));
    }

    public Mono<JsonNode> report(UUID incidentId) {
        return request(JsonNode.class, builder -> builder.path("/api/v1/incidents/{id}/report").build(incidentId));
    }

    private <T> Mono<T> request(Class<T> responseType,
            java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uri) {
        if (!settings.available()) {
            return Mono.error(unavailable());
        }
        return webClient.get().uri(uri).retrieve()
                .onStatus(status -> status.value() == 404,
                        response -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到对应诊断数据")))
                .onStatus(status -> status.value() == 401,
                        response -> Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "智能运维鉴权配置无效")))
                .onStatus(status -> status.isError(), response -> Mono.error(unavailable()))
                .bodyToMono(responseType).timeout(settings.timeout())
                .onErrorMap(error -> error instanceof ResponseStatusException ? error : unavailable());
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "智能运维服务暂时不可用");
    }

    public record IncidentPage(List<IncidentSummary> items, int page, int size, long total) { }
    public record IncidentSummary(UUID id, String title, String primaryService, String alertName, String severity,
            String status, Instant startedAt, Instant lastAlertAt, Instant createdAt, boolean hasReport) { }
    public record IncidentDetail(UUID id, String title, String primaryService, String alertName, String severity,
            String status, Instant startedAt, Instant lastAlertAt, UUID diagnosisRunId, String diagnosisRunStatus,
            Integer attempt, String failureCode) { }
    public record ShadowSummary(String service, int windowHours, long totalIncidents, long activeIncidents,
            long resolvedIncidents, long reportCount, long supportedReports, long abstainedReports,
            long evidenceCompleteReports, long averageReportLatencyMs) { }
}
