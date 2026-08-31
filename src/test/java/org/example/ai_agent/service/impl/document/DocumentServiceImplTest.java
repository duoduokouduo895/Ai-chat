package org.example.ai_agent.service.impl.document;

import org.example.ai_agent.dto.document.DocumentDto;
import org.example.ai_agent.dto.document.DocumentListProjection;
import org.example.ai_agent.dto.document.AdminDocumentSummaryProjection;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.entity.KnowledgeBase;
import org.example.ai_agent.entity.KnowledgeDocument;
import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository;
import org.example.ai_agent.service.DocumentIngestionService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.OssService;
import org.example.ai_agent.service.document.DocumentUploadValidator;
import org.example.ai_agent.service.impl.knowledge.KnowledgeAssetCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("minimal")
@Tag("high-risk")
class DocumentServiceImplTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;
    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Mock
    private OssService ossService;
    @Mock
    private DocumentIngestionService documentIngestionService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private KnowledgeAssetCleanupService knowledgeAssetCleanupService;
    @Mock
    private DocumentUploadValidator documentUploadValidator;
    @Mock
    private TransactionalOperator transactionalOperator;
    @Mock
    private MultipartFile multipartFile;

    private DocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentServiceImpl(
                documentRepository,
                knowledgeBaseService,
                ossService,
                documentIngestionService,
                userRepository,
                knowledgeAssetCleanupService,
                documentUploadValidator,
                transactionalOperator);
        ReflectionTestUtils.setField(service, "supportedTypes", new String[]{"md"});
    }

    @Test
    void uploadDocument_shouldDeleteOssObjectWhenDatabaseTransactionFails() throws Exception {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(21L);
        OssService.OssFileInfo ossFileInfo = new OssService.OssFileInfo(
                "knowledge/kb_21/file.md", "https://oss.example/file.md", 4L, "text/markdown");

        when(multipartFile.getOriginalFilename()).thenReturn("file.md");
        when(multipartFile.getSize()).thenReturn(4L);
        when(multipartFile.getContentType()).thenReturn("text/markdown");
        when(multipartFile.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("test".getBytes()));
        when(knowledgeBaseService.getAuthorizedKnowledgeBase(21L, 42L, false, true))
                .thenReturn(Mono.just(knowledgeBase));
        when(ossService.uploadKnowledgeDocument(eq(21L), eq("file.md"), any(), eq(4L), eq("text/markdown")))
                .thenReturn(Mono.just(ossFileInfo));
        when(documentRepository.save(any(KnowledgeDocument.class)))
                .thenReturn(Mono.error(new RuntimeException("database unavailable")));
        when(ossService.deleteFile("knowledge/kb_21/file.md")).thenReturn(Mono.just(true));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(service.uploadDocument(multipartFile, 21L, "auto", true, 42L, false))
                .expectErrorMatches(error -> error instanceof RuntimeException
                        && "database unavailable".equals(error.getMessage()))
                .verify();

        verify(ossService).deleteFile("knowledge/kb_21/file.md");
        verify(documentIngestionService, never()).startIngestion(anyLong(), anyLong(), any(), any());
    }

    @Test
    void uploadDocument_shouldPreserveDatabaseErrorWhenOssCleanupAlsoFails() throws Exception {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(21L);
        OssService.OssFileInfo ossFileInfo = new OssService.OssFileInfo(
                "knowledge/kb_21/file.md", "https://oss.example/file.md", 4L, "text/markdown");

        when(multipartFile.getOriginalFilename()).thenReturn("file.md");
        when(multipartFile.getSize()).thenReturn(4L);
        when(multipartFile.getContentType()).thenReturn("text/markdown");
        when(multipartFile.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("test".getBytes()));
        when(knowledgeBaseService.getAuthorizedKnowledgeBase(21L, 42L, false, true))
                .thenReturn(Mono.just(knowledgeBase));
        when(ossService.uploadKnowledgeDocument(eq(21L), eq("file.md"), any(), eq(4L), eq("text/markdown")))
                .thenReturn(Mono.just(ossFileInfo));
        when(documentRepository.save(any(KnowledgeDocument.class)))
                .thenReturn(Mono.error(new RuntimeException("database unavailable")));
        when(ossService.deleteFile("knowledge/kb_21/file.md"))
                .thenReturn(Mono.error(new RuntimeException("oss unavailable")));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(service.uploadDocument(multipartFile, 21L, "auto", true, 42L, false))
                .expectErrorMatches(error -> error instanceof RuntimeException
                        && "database unavailable".equals(error.getMessage()))
                .verify();

        verify(ossService).deleteFile("knowledge/kb_21/file.md");
    }

    @Test
    void getDocument_shouldRejectWhenKnowledgeBaseAccessDenied() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(11L);
        document.setKnowledgeBaseId(20L);

        when(documentRepository.findById(11L)).thenReturn(Mono.just(document));
        when(knowledgeBaseService.getAuthorizedKnowledgeBase(20L, 300L, false, false))
                .thenReturn(Mono.error(new SecurityException("当前用户无权访问该知识库")));

        StepVerifier.create(service.getDocument(11L, 300L, false))
                .expectErrorSatisfies(error -> {
                    org.junit.jupiter.api.Assertions.assertInstanceOf(SecurityException.class, error);
                    org.junit.jupiter.api.Assertions.assertEquals("当前用户无权访问该知识库", error.getMessage());
                })
                .verify();
    }

    @Test
    void getDocument_shouldReturnDocumentWhenKnowledgeBaseAccessGranted() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(12L);
        document.setKnowledgeBaseId(21L);
        document.setUploaderId(501L);
        document.setFileName("readme.md");
        document.setTitle("readme.md");
        document.setFileType("md");
        document.setParseStatus("COMPLETED");
        document.setEmbeddingStatus("COMPLETED");
        document.setChunkCount(1);
        document.setIsEmbedded(true);

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(21L);
        knowledgeBase.setCreatorId(100L);
        knowledgeBase.setIsPublic(true);

        KnowledgeBaseDto knowledgeBaseDto = new KnowledgeBaseDto();
        knowledgeBaseDto.setId(21L);
        knowledgeBaseDto.setName("公开知识库");

        User user = new User();
        user.setUserId(501L);
        user.setUsername("uploader");

        when(documentRepository.findById(12L)).thenReturn(Mono.just(document));
        when(knowledgeBaseService.getAuthorizedKnowledgeBase(21L, 300L, false, false))
                .thenReturn(Mono.just(knowledgeBase));
        when(knowledgeBaseService.getKnowledgeBase(21L)).thenReturn(Mono.just(knowledgeBaseDto));
        when(userRepository.findById(501L)).thenReturn(Mono.just(user));

        StepVerifier.create(service.getDocument(12L, 300L, false))
                .assertNext(dto -> assertDocumentDto(dto))
                .verifyComplete();
    }

    @Test
    void deleteDocument_shouldNotCleanupWhenWriteAccessDenied() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(13L);
        document.setKnowledgeBaseId(22L);

        when(documentRepository.findById(13L)).thenReturn(Mono.just(document));
        when(knowledgeBaseService.getAuthorizedKnowledgeBase(22L, 300L, false, true))
                .thenReturn(Mono.error(new SecurityException("当前用户无权修改该知识库")));

        StepVerifier.create(service.deleteDocument(13L, 300L, false))
                .expectError(SecurityException.class)
                .verify();

        verify(knowledgeAssetCleanupService, never()).deleteDocumentAggregate(document);
    }

    @Test
    void getDocuments_shouldUseSummaryProjectionWithoutSecondaryQueriesOrContent() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(21L);

        DocumentListProjection projection = new DocumentListProjection();
        projection.setId(14L);
        projection.setKnowledgeBaseId(21L);
        projection.setKnowledgeBaseName("公开知识库");
        projection.setUploaderId(501L);
        projection.setUploaderName("uploader");
        projection.setFileName("summary.md");
        projection.setChunkCount(1);
        projection.setEmbeddingStatus("COMPLETED");

        when(knowledgeBaseService.getAuthorizedKnowledgeBase(21L, 300L, false, false))
                .thenReturn(Mono.just(knowledgeBase));
        when(documentRepository.findSummariesByKnowledgeBaseId(21L, 10, 0L))
                .thenReturn(Flux.just(projection));

        StepVerifier.create(service.getDocuments(21L, 0, 10, 300L, false))
                .assertNext(dto -> {
                    org.junit.jupiter.api.Assertions.assertEquals(14L, dto.getId());
                    org.junit.jupiter.api.Assertions.assertEquals("公开知识库", dto.getKnowledgeBaseName());
                    org.junit.jupiter.api.Assertions.assertEquals("uploader", dto.getUploaderName());
                    org.junit.jupiter.api.Assertions.assertEquals("summary.md", dto.getFileName());
                    org.junit.jupiter.api.Assertions.assertNull(dto.getContent());
                })
                .verifyComplete();

        verify(knowledgeBaseService, never()).getKnowledgeBase(anyLong());
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void getAllDocumentsPage_shouldReturnFilteredDatabasePageAndGlobalSummary() {
        DocumentListProjection projection = new DocumentListProjection();
        projection.setId(15L);
        projection.setFileName("合同.md");

        AdminDocumentSummaryProjection summary = new AdminDocumentSummaryProjection();
        summary.setDocumentCount(21L);
        summary.setUploaderCount(7L);
        summary.setTotalFileSize(4096L);

        when(documentRepository.findAdminPage("合同", "COMPLETED", "EMBEDDED", 20, 20L))
                .thenReturn(Flux.just(projection));
        when(documentRepository.countAdminPage("合同", "COMPLETED", "EMBEDDED"))
                .thenReturn(Mono.just(1L));
        when(documentRepository.summarizeForAdmin()).thenReturn(Mono.just(summary));

        StepVerifier.create(service.getAllDocumentsPage(1, 20, " 合同 ", "COMPLETED", "EMBEDDED"))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertEquals(1L, result.getTotal());
                    org.junit.jupiter.api.Assertions.assertEquals(15L, result.getItems().get(0).getId());
                    org.junit.jupiter.api.Assertions.assertEquals(21L, result.getSummary().get("documentCount"));
                    org.junit.jupiter.api.Assertions.assertEquals(7L, result.getSummary().get("uploaderCount"));
                    org.junit.jupiter.api.Assertions.assertEquals(4096L, result.getSummary().get("totalFileSize"));
                })
                .verifyComplete();
    }

    private void assertDocumentDto(DocumentDto dto) {
        org.junit.jupiter.api.Assertions.assertEquals(12L, dto.getId());
        org.junit.jupiter.api.Assertions.assertEquals("公开知识库", dto.getKnowledgeBaseName());
        org.junit.jupiter.api.Assertions.assertEquals("uploader", dto.getUploaderName());
        org.junit.jupiter.api.Assertions.assertEquals("readme.md", dto.getFileName());
    }
}
