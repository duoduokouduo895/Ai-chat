package org.example.ai_agent.service.impl.document;

import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.entity.DocumentProcessingRecord;
import org.example.ai_agent.repository.document.DocumentProcessingRecordRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("minimal")
@Tag("high-risk")
class DocumentProcessingRecordServiceImplTest {

    @Mock
    private DocumentProcessingRecordRepository recordRepository;
    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;

    private DocumentProcessingRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentProcessingRecordServiceImpl();
        ReflectionTestUtils.setField(service, "recordRepository", recordRepository);
        ReflectionTestUtils.setField(service, "knowledgeDocumentRepository", knowledgeDocumentRepository);
        ReflectionTestUtils.setField(service, "claimTimeoutMinutes", 30L);
    }

    @Test
    void startProcessing_shouldRejectActiveClaim() {
        DocumentProcessingRecord record = processingRecord(LocalDateTime.now());

        when(knowledgeDocumentRepository.existsById(9L)).thenReturn(Mono.just(true));
        when(recordRepository.findFirstByDocumentIdAndProcessingTypeOrderByIdDesc(9L, "PARSE"))
                .thenReturn(Mono.just(record));

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
        when(recordRepository.save(record)).thenReturn(Mono.just(record));

        StepVerifier.create(service.startProcessing(9L, "PARSE"))
                .expectNext(record)
                .verifyComplete();

        org.junit.jupiter.api.Assertions.assertNotNull(record.getStartTime());
        org.junit.jupiter.api.Assertions.assertEquals(DocumentProcessingConstants.Status.PROCESSING, record.getStatus());
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
