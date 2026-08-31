package org.example.ai_agent.controller;

import org.example.ai_agent.exception.GlobalExceptionHandler;
import org.example.ai_agent.controller.support.RequestActorResolver;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ConversationControllerValidationTest {

    @Mock
    private ConversationService conversationService;
    @Mock
    private UserRepository userRepository;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new ConversationController(conversationService,
                        new RequestActorResolver(userRepository)))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void addMessage_shouldRejectOversizedContentBeforeLoadingTheConversation() {
        String oversizedContent = "x".repeat(20_001);

        webTestClient.post()
                .uri("/api/conversations/1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"content\":\"" + oversizedContent + "\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.message").isEqualTo("消息内容长度不能超过20000个字符");

        verifyNoInteractions(conversationService, userRepository);
    }

    @Test
    void addMessage_shouldRejectObjectContentInsteadOfCastingItAtRuntime() {
        webTestClient.post()
                .uri("/api/conversations/1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"content\":{\"text\":\"not a string\"}}")
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(conversationService, userRepository);
    }
}
