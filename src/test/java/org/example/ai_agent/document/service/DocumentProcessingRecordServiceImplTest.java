package org.example.ai_agent.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_agent.document.domain.DocumentProcessingConstants;
import org.example.ai_agent.document.entity.DocumentProcessingRecord;
import org.example.ai_agent.document.repository.DocumentProcessingRecordRepository;
import org.example.ai_agent.knowledge.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("minimal")
@Tag("high-risk")
class DocumentProcessingRecordServiceImplTest {

    @Mock
    private DocumentProcessingRecordRepository recordRepository;
    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;
    @Mock
    private ObjectMapper objectMapper;

    private DocumentProcessingRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentProcessingRecordServiceImpl(
                recordRepository,
                knowledgeDocumentRepository,
                objectMapper);
        ReflectionTestUtils.setField(service, "claimTimeoutMinutes", 30L);
    }

    @Test
    void startProcessing_shouldRejectActiveClaim() {
        DocumentProcessingRecord record = processingRecord(LocalDateTime.now());

        when(knowledgeDocumentRepository.existsById(9L)).thenReturn(Mono.just(true));
        when(recordRepository.findFirstByDocumentIdAndProcessingTypeOrderByIdDesc(9L, "PARSE"))
                .thenReturn(Mono.just(record));
        when(recordRepository.claimProcessing(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(DocumentProcessingConstants.Status.PROCESSING),
                any(), any(), any())).thenReturn(Mono.just(0));
        when(recordRepository.findById(1L)).thenReturn(Mono.just(record));

        StepVerifier.create(service.startProcessing(9L, "PARSE"))
                .expectError(DocumentProcessingAlreadyHandledException.class)
                .verify();

        verify(recordRepository, never()).save(any());
    }

    @Test
    void startProcessing_shouldReclaimExpiredClaim() {
        DocumentProcessingRecord record = processingRecord(LocalDateTime.now().minusMinutes(31));

        when(knowledgeDocumentRepository.existsById(9L)).thenReturn(Mono.just(true));
        when(recordRepository.findFirstByDocumentIdAndProcessingTypeOrderByIdDesc(9L, "PARSE"))
                .thenReturn(Mono.just(record));
        when(recordRepository.claimProcessing(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(DocumentProcessingConstants.Status.PROCESSING),
                any(), any(), any())).thenReturn(Mono.just(1));
        when(recordRepository.findById(1L)).thenReturn(Mono.just(record));

        StepVerifier.create(service.startProcessing(9L, "PARSE"))
                .expectNext(record)
                .verifyComplete();

        verify(recordRepository).claimProcessing(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(DocumentProcessingConstants.Status.PROCESSING),
                any(), any(), any());
    }

    @Test
    void incrementRetryCount_shouldResetStatusToPending() {
        DocumentProcessingRecord record = processingRecord(LocalDateTime.now());
        record.setRetryCount(1);

        when(recordRepository.findFirstByDocumentIdAndProcessingTypeOrderByIdDesc(9L, "PARSE"))
                .thenReturn(Mono.just(record));
        when(recordRepository.save(record)).thenReturn(Mono.just(record));

        StepVerifier.create(service.incrementRetryCount(9L, "PARSE"))
                .expectNext(record)
                .verifyComplete();

        org.junit.jupiter.api.Assertions.assertEquals(2, record.getRetryCount());
        org.junit.jupiter.api.Assertions.assertEquals(DocumentProcessingConstants.Status.PENDING, record.getStatus());
    }

    @Test
    void startProcessing_shouldRejectUnknownProcessingTypeBeforeRepositoryAccess() {
        StepVerifier.create(service.startProcessing(9L, "UNKNOWN"))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(recordRepository, knowledgeDocumentRepository);
    }

    private DocumentProcessingRecord processingRecord(LocalDateTime updatedAt) {
        DocumentProcessingRecord record = new DocumentProcessingRecord();
        record.setId(1L);
        record.setDocumentId(9L);
        record.setProcessingType("PARSE");
        record.setStatus(DocumentProcessingConstants.Status.PROCESSING);
        record.setRetryCount(0);
        record.setUpdatedAt(updatedAt);
        return record;
    }
}
