package org.example.ai_agent.chat.controller;

import org.example.ai_agent.chat.dto.ChatRequest;
import org.example.ai_agent.llm.ChatModelRoutingPolicy;
import org.example.ai_agent.auth.security.RequestActorResolver;
import org.example.ai_agent.auth.entity.User;
import org.example.ai_agent.auth.repository.UserRepository;
import org.example.ai_agent.auth.service.RepositoryActorIdentityLookup;
import org.example.ai_agent.chat.service.AiChatService;
import org.example.ai_agent.common.admission.ChatAdmissionService;
import org.example.ai_agent.llm.AiModelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("high-risk")
class AiControllerTest {

    @Mock
    private AiModelRegistry aiModelRegistry;
    @Mock
    private AiChatService aiChatService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatAdmissionService chatAdmissionService;
    @Mock
    private ChatModelRoutingPolicy chatModelRoutingPolicy;

    private AiController controller;

    @BeforeEach
    void setUp() {
        controller = new AiController(aiModelRegistry, aiChatService,
                new RequestActorResolver(new RepositoryActorIdentityLookup(userRepository)), chatAdmissionService,
                chatModelRoutingPolicy);
    }

    @Test
    void streamChat_shouldResolveUserIdFromUsernameWhenAuthenticationDetailsUnavailable() {
        ChatRequest request = ChatRequest.builder()
                .prompt("你好")
                .model("test-model")
                .build();

        User user = new User();
        user.setUserId(42L);
        user.setUsername("alice");

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("alice", "n/a", List.of());
        authentication.setDetails("not-a-number");

        when(userRepository.findByUsername("alice")).thenReturn(Mono.just(user));
        when(chatModelRoutingPolicy.resolve(request)).thenReturn("test-model");
        when(chatAdmissionService.admit(org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq(false), any()))
                .thenReturn(Mono.just(ChatAdmissionService.AdmissionLease.noop()));
        when(aiChatService.chat(any())).thenReturn(Flux.just(ServerSentEvent.<String>builder()
                .event("message")
                .data("中文输出")
                .build()));

        StepVerifier.create(controller.streamChat(request, authentication).map(ServerSentEvent::data))
                .expectNext("中文输出")
                .verifyComplete();

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiChatService).chat(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(42L, captor.getValue().getUserId());
        org.junit.jupiter.api.Assertions.assertEquals("test-model", captor.getValue().getModel());
    }
}
