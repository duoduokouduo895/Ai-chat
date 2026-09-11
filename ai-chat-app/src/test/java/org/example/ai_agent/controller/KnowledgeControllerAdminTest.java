package org.example.ai_agent.controller;

import org.example.ai_agent.dto.document.DocumentDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseCreateDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.controller.support.KnowledgeAccessContextResolver;
import org.example.ai_agent.controller.support.KnowledgeDocumentUploadFacade;
import org.example.ai_agent.controller.support.RequestActorResolver;
import org.example.ai_agent.controller.support.ReactiveMultipartFormReader;
import org.example.ai_agent.config.properties.DocumentUploadProperties;
import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.DocumentChunkService;
import org.example.ai_agent.service.DocumentProcessingRecordService;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.KnowledgeChunkSearchService;
import org.example.ai_agent.service.RagService;
import org.example.ai_agent.service.impl.identity.RepositoryActorIdentityLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("high-risk")
class KnowledgeControllerAdminTest {

    @Mock
    private RagService ragService;
    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Mock
    private DocumentService documentService;
    @Mock
    private DocumentProcessingRecordService recordService;
    @Mock
    private DocumentChunkService documentChunkService;
    @Mock
    private KnowledgeChunkSearchService knowledgeChunkSearchService;
    @Mock
    private UserRepository userRepository;

    private KnowledgeController controller;
    private KnowledgeDocumentController documentController;

    @BeforeEach
    void setUp() {
        KnowledgeAccessContextResolver accessContextResolver =
                new KnowledgeAccessContextResolver(knowledgeBaseService,
                        new RepositoryActorIdentityLookup(userRepository),
                        new RequestActorResolver(new RepositoryActorIdentityLookup(userRepository)));
        ReactiveMultipartFormReader formReader = new ReactiveMultipartFormReader();
        KnowledgeDocumentUploadFacade uploadFacade =
                new KnowledgeDocumentUploadFacade(documentService, accessContextResolver, formReader,
                        new DocumentUploadProperties());
        controller = new KnowledgeController(
                ragService,
                knowledgeBaseService,
                knowledgeChunkSearchService,
                accessContextResolver, org.mockito.Mockito.mock(org.example.ai_agent.service.impl.knowledge.KnowledgeCleanupCommands.class));
        documentController = new KnowledgeDocumentController(
                documentService, documentChunkService, accessContextResolver, uploadFacade, org.mockito.Mockito.mock(org.example.ai_agent.service.impl.knowledge.KnowledgeCleanupCommands.class));
    }

    @Test
    void createKnowledgeBaseForAdmin_shouldCreateForSpecifiedCreator() {
        Authentication admin = adminAuthentication();
        User creator = new User();
        creator.setUserId(88L);

        KnowledgeBaseCreateDto request = new KnowledgeBaseCreateDto();
        request.setName("管理员代创建知识库");

        KnowledgeBaseDto response = new KnowledgeBaseDto();
        response.setId(1L);
        response.setCreatorId(88L);
        response.setName("管理员代创建知识库");

        when(userRepository.findById(88L)).thenReturn(Mono.just(creator));
        when(knowledgeBaseService.createKnowledgeBase(request, 88L)).thenReturn(Mono.just(response));

        StepVerifier.create(controller.createKnowledgeBaseForAdmin(request, 88L, admin))
                .expectNext(response)
                .verifyComplete();
    }

    @Test
    void createKnowledgeBaseForAdmin_shouldRejectNonAdmin() {
        Authentication normalUser = new UsernamePasswordAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        KnowledgeBaseCreateDto request = new KnowledgeBaseCreateDto();
        request.setName("普通用户冒充管理员");
        when(userRepository.findById(88L)).thenReturn(Mono.empty());

        StepVerifier.create(controller.createKnowledgeBaseForAdmin(request, 88L, normalUser))
                .expectErrorSatisfies(error -> {
                    org.junit.jupiter.api.Assertions.assertInstanceOf(SecurityException.class, error);
                    org.junit.jupiter.api.Assertions.assertEquals("当前用户无权访问管理员资源", error.getMessage());
                })
                .verify();

        verify(knowledgeBaseService, never()).createKnowledgeBase(any(), eq(88L));
    }

    @Test
    void uploadDocumentDirectForAdmin_shouldFallbackToKnowledgeBaseCreatorWhenUploaderMissing() {
        Authentication admin = adminAuthentication();
        ServerWebExchange exchange = org.mockito.Mockito.mock(ServerWebExchange.class);

        Part knowledgeBaseIdPart = mockTextPart("66");
        Part parseModePart = mockTextPart("auto");
        Part vectorizationPart = mockTextPart("true");
        FilePart filePart = mockFilePart("admin.txt", "管理员上传");

        org.springframework.util.LinkedMultiValueMap<String, Part> multipartData = new org.springframework.util.LinkedMultiValueMap<>();
        multipartData.add("file", filePart);
        multipartData.add("knowledgeBaseId", knowledgeBaseIdPart);
        multipartData.add("parseMode", parseModePart);
        multipartData.add("enableVectorization", vectorizationPart);

        User creator = new User();
        creator.setUserId(501L);
        KnowledgeBaseDto knowledgeBaseDto = new KnowledgeBaseDto();
        knowledgeBaseDto.setId(66L);
        knowledgeBaseDto.setCreatorId(501L);
        DocumentDto uploaded = new DocumentDto();
        uploaded.setId(9L);
        uploaded.setUploaderId(501L);
        uploaded.setFileName("admin.txt");

        when(exchange.getMultipartData()).thenReturn(Mono.just(multipartData));
        when(knowledgeBaseService.getKnowledgeBase(66L)).thenReturn(Mono.just(knowledgeBaseDto));
        when(userRepository.findById(501L)).thenReturn(Mono.just(creator));
        when(documentService.uploadDocument(any(), eq(66L), eq("auto"), eq(true), eq(501L), eq(true)))
                .thenReturn(Mono.just(uploaded));

        StepVerifier.create(documentController.uploadDocumentDirectForAdmin(exchange, admin))
                .expectNext(uploaded)
                .verifyComplete();

        ArgumentCaptor<org.springframework.web.multipart.MultipartFile> captor =
                ArgumentCaptor.forClass(org.springframework.web.multipart.MultipartFile.class);
        verify(documentService).uploadDocument(captor.capture(), eq(66L), eq("auto"), eq(true), eq(501L), eq(true));
        org.junit.jupiter.api.Assertions.assertEquals("admin.txt", captor.getValue().getOriginalFilename());
    }

    private Authentication adminAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Part mockTextPart(String value) {
        Part part = org.mockito.Mockito.mock(Part.class);
        when(part.content()).thenReturn(Flux.just(new DefaultDataBufferFactory()
                .wrap(value.getBytes(StandardCharsets.UTF_8))));
        return part;
    }

    private FilePart mockFilePart(String filename, String content) {
        FilePart filePart = org.mockito.Mockito.mock(FilePart.class);
        when(filePart.filename()).thenReturn(filename);
        when(filePart.transferTo(org.mockito.ArgumentMatchers.any(java.nio.file.Path.class))).thenAnswer(invocation -> {
            java.nio.file.Path path = invocation.getArgument(0);
            return Mono.fromRunnable(() -> {
                try {
                    java.nio.file.Files.write(path, content.getBytes(StandardCharsets.UTF_8));
                } catch (java.io.IOException error) {
                    throw new IllegalStateException(error);
                }
            });
        });
        return filePart;
    }
}
