package org.example.ai_agent.service.impl.knowledge;

import org.example.ai_agent.entity.KnowledgeBase;
import org.example.ai_agent.repository.knowledge.KnowledgeBaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Test
    void missingSelectionUsesOnlyPublicActiveKnowledgeBases() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(9L);
        knowledgeBase.setStatus("ACTIVE");

        when(knowledgeBaseRepository.findByIsPublicAndStatusOrderByCreatedAtDesc(
                eq(true), eq("ACTIVE"), org.mockito.ArgumentMatchers.<Pageable>any()))
                .thenReturn(Flux.just(knowledgeBase));

        RagServiceImpl service = new RagServiceImpl();
        ReflectionTestUtils.setField(service, "knowledgeBaseRepository", knowledgeBaseRepository);

        Flux<KnowledgeBase> resolved = ReflectionTestUtils.invokeMethod(
                service, "resolveKnowledgeBases", List.of(), 100L, false);

        StepVerifier.create(resolved)
                .expectNext(knowledgeBase)
                .verifyComplete();

        verify(knowledgeBaseRepository)
                .findByIsPublicAndStatusOrderByCreatedAtDesc(
                        eq(true), eq("ACTIVE"), org.mockito.ArgumentMatchers.<Pageable>any());
        verify(knowledgeBaseRepository, never()).findByStatus(any(String.class));
    }

    @Test
    void explicitSelectionRejectsPrivateKnowledgeBaseOwnedByAnotherUser() {
        KnowledgeBase knowledgeBase = knowledgeBase(11L, 100L, false, "ACTIVE");
        when(knowledgeBaseRepository.findAllById(any(Iterable.class))).thenReturn(Flux.just(knowledgeBase));

        RagServiceImpl service = service();
        Flux<KnowledgeBase> resolved = ReflectionTestUtils.invokeMethod(
                service, "resolveKnowledgeBases", List.of(11L), 200L, false);

        StepVerifier.create(resolved)
                .expectErrorMatches(error -> error instanceof SecurityException
                        && error.getMessage().contains("无权访问"))
                .verify();
    }

    @Test
    void explicitSelectionBatchLoadsDistinctKnowledgeBases() {
        KnowledgeBase knowledgeBase = knowledgeBase(12L, 100L, true, "ACTIVE");
        when(knowledgeBaseRepository.findAllById(any(Iterable.class))).thenReturn(Flux.just(knowledgeBase));

        RagServiceImpl service = service();
        Flux<KnowledgeBase> resolved = ReflectionTestUtils.invokeMethod(
                service, "resolveKnowledgeBases", List.of(12L, 12L), 200L, false);

        StepVerifier.create(resolved)
                .expectNext(knowledgeBase)
                .verifyComplete();

        ArgumentCaptor<Iterable<Long>> idsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(knowledgeBaseRepository).findAllById(idsCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(12L),
                java.util.stream.StreamSupport.stream(idsCaptor.getValue().spliterator(), false).toList());
        verify(knowledgeBaseRepository, never()).findById(any(Long.class));
    }

    private RagServiceImpl service() {
        RagServiceImpl service = new RagServiceImpl();
        ReflectionTestUtils.setField(service, "knowledgeBaseRepository", knowledgeBaseRepository);
        return service;
    }

    private KnowledgeBase knowledgeBase(Long id, Long creatorId, boolean isPublic, String status) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(id);
        knowledgeBase.setCreatorId(creatorId);
        knowledgeBase.setIsPublic(isPublic);
        knowledgeBase.setStatus(status);
        return knowledgeBase;
    }
}
