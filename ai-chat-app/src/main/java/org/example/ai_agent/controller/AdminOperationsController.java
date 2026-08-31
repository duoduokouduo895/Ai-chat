package org.example.ai_agent.controller;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.example.ai_agent.service.operations.AidiagClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** ADMIN-only BFF for the AIDiag read API. SecurityConfig applies ROLE_ADMIN before this controller. */
@Validated
@RestController
@RequestMapping("/api/admin/operations")
public class AdminOperationsController {

    private static final Set<String> STATUSES = Set.of("OPEN", "DIAGNOSING", "DIAGNOSED", "FAILED", "RESOLVED", "CLOSED");
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "CRITICAL");

    private final AidiagClient aidiagClient;

    public AdminOperationsController(AidiagClient aidiagClient) {
        this.aidiagClient = aidiagClient;
    }

    @GetMapping("/incidents")
    public Mono<AidiagClient.IncidentPage> incidents(@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z_]{1,32}") String status,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z_]{1,32}") String severity) {
        validateOptional(status, STATUSES, "status");
        validateOptional(severity, SEVERITIES, "severity");
        return aidiagClient.incidents(page, size, status, severity);
    }

    @GetMapping("/summary")
    public Mono<AidiagClient.ShadowSummary> summary(
            @RequestParam(defaultValue = "24") @Min(1) @Max(720) int windowHours) {
        return aidiagClient.summary(windowHours);
    }

    @GetMapping("/incidents/{incidentId}")
    public Mono<AidiagClient.IncidentDetail> detail(@PathVariable UUID incidentId) {
        return aidiagClient.detail(incidentId);
    }

    @GetMapping("/incidents/{incidentId}/report")
    public Mono<com.fasterxml.jackson.databind.JsonNode> report(@PathVariable UUID incidentId) {
        return aidiagClient.report(incidentId);
    }

    private static void validateOptional(String value, Set<String> allowed, String field) {
        if (value != null && !allowed.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is not supported");
        }
    }
}
