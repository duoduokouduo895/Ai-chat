package org.example.ai_agent.document.pipeline;

import org.example.ai_agent.document.config.DocumentProcessingProperties;
import org.example.ai_agent.document.dto.DocumentProcessingMessage;
import org.example.ai_agent.document.entity.IngestionRunRow;
import org.example.ai_agent.document.domain.IngestionStage;
import org.example.ai_agent.document.domain.StageClaimDisposition;
import org.example.ai_agent.document.domain.StageLeaseClaim;
import org.example.ai_agent.document.repository.IngestionRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionRunLeaseServiceTest {

    private static final String RUN_ID = "00000000-0000-0000-0000-000000000301";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 2, 1, 30);

    @Mock
    private IngestionRunRepository runRepository;

    private IngestionRunLeaseService service;

    @BeforeEach
    void setUp() {
        DocumentProcessingProperties properties = new DocumentProcessingProperties();
        properties.setIngestionLeaseDuration(Duration.ofMinutes(2));
        Clock clock = Clock.fixed(Instant.parse("2026-09-02T01:30:00Z"), ZoneOffset.UTC);
        service = new IngestionRunLeaseService(runRepository, properties, clock);
    }

    @Test
    void claimAndAdvanceRequireTheSameFencingToken() {
        IngestionRunRow pending = run("PARSE", "PENDING");
        IngestionRunRow processing = run("PARSE", "PROCESSING");
        when(runRepository.findById(RUN_ID)).thenReturn(Mono.just(pending), Mono.just(processing));
        when(runRepository.claimStage(eq(RUN_ID), eq("PARSE"), anyString(), any(), eq(NOW)))
                .thenReturn(Mono.just(1));
        when(runRepository.advanceStage(eq(RUN_ID), eq("PARSE"), eq("CHUNK"), anyString(), eq(NOW)))
                .thenReturn(Mono.just(1));

        StageLeaseClaim claim = service.claim(message()).block();

        assertNotNull(claim);
        assertEquals(StageClaimDisposition.CLAIMED, claim.disposition());
        assertNotNull(claim.leaseToken());
        StepVerifier.create(service.completeStage(claim, IngestionStage.CHUNK)).verifyComplete();
        verify(runRepository).advanceStage(
                RUN_ID, "PARSE", "CHUNK", claim.leaseToken(), NOW);
    }

    @Test
    void messageForAnEarlierStageIsClassifiedAsAlreadyCompleted() {
        IngestionRunRow current = run("CHUNK", "PENDING");
        when(runRepository.findById(RUN_ID)).thenReturn(Mono.just(current));
        when(runRepository.claimStage(eq(RUN_ID), eq("PARSE"), anyString(), any(), eq(NOW)))
                .thenReturn(Mono.just(0));

        StepVerifier.create(service.claim(message()))
                .assertNext(claim -> assertEquals(
                        StageClaimDisposition.ALREADY_COMPLETED, claim.disposition()))
                .verifyComplete();
    }

    @Test
    void staleLeaseCannotAdvanceTheRun() {
        StageLeaseClaim staleClaim = new StageLeaseClaim(
                StageClaimDisposition.CLAIMED,
                RUN_ID,
                IngestionStage.PARSE,
                IngestionStage.PARSE,
                org.example.ai_agent.document.domain.IngestionRunStatus.PROCESSING,
                "stale-token",
                NOW.minusSeconds(1));
        when(runRepository.advanceStage(RUN_ID, "PARSE", "CHUNK", "stale-token", NOW))
                .thenReturn(Mono.just(0));

        StepVerifier.create(service.completeStage(staleClaim, IngestionStage.CHUNK))
                .expectError(IngestionLeaseLostException.class)
                .verify();
    }

    @Test
    void messageIdentityMustMatchTheImmutableRunIdentity() {
        IngestionRunRow run = run("PARSE", "PENDING");
        run.setGeneration(2L);
        when(runRepository.findById(RUN_ID)).thenReturn(Mono.just(run));

        StepVerifier.create(service.claim(message()))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && error.getMessage().contains("does not match"))
                .verify();
    }

    private IngestionRunRow run(String stage, String status) {
        IngestionRunRow run = new IngestionRunRow();
        run.setId(RUN_ID);
        run.setDocumentId(301L);
        run.setKnowledgeBaseId(300L);
        run.setVersionId(302L);
        run.setGeneration(1L);
        run.setCurrentStage(stage);
        run.setStatus(status);
        run.setAttempt(0);
        return run;
    }

    private DocumentProcessingMessage message() {
        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(
                301L, 300L, "auto", true);
        message.setRunId(RUN_ID);
        message.setVersionId(302L);
        message.setGeneration(1L);
        return message;
    }
}
