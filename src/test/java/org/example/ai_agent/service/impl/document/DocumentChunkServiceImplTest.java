package org.example.ai_agent.service.impl.document;

import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.repository.document.DocumentChunkBulkUpdateRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("minimal")
@Tag("high-risk")
class DocumentChunkServiceImplTest {

    @Mock
    private KnowledgeDocumentChunkRepository chunkRepository;
    @Mock
    private DocumentChunkBulkUpdateRepository chunkBulkUpdateRepository;

    private DocumentChunkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentChunkServiceImpl();
        ReflectionTestUtils.setField(service, "chunkRepository", chunkRepository);
        ReflectionTestUtils.setField(service, "chunkBulkUpdateRepository", chunkBulkUpdateRepository);
    }

    @Test
    void batchUpdateChunkEmbeddingStatus_shouldUseSingleBulkUpdate() {
        List<Long> chunkIds = List.of(1L, 2L);
        List<String> embeddingIds = List.of("embedding-1", "embedding-2");

        when(chunkBulkUpdateRepository.updateEmbeddingStatus(
                chunkIds, embeddingIds, DocumentProcessingConstants.Status.COMPLETED))
                .thenReturn(Mono.just(2L));

        StepVerifier.create(service.batchUpdateChunkEmbeddingStatus(
                        chunkIds, embeddingIds, DocumentProcessingConstants.Status.COMPLETED))
                .expectNext(true)
                .verifyComplete();

        verify(chunkBulkUpdateRepository).updateEmbeddingStatus(
                chunkIds, embeddingIds, DocumentProcessingConstants.Status.COMPLETED);
    }

    @Test
    void countDocumentChunks_shouldConvertLongCount() {
        when(chunkRepository.countByDocumentId(9L)).thenReturn(Mono.just(3L));

        StepVerifier.create(service.countDocumentChunks(9L))
                .expectNext(3)
                .verifyComplete();
    }
}
