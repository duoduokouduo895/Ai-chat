package org.example.ai_agent.controller;

import org.example.ai_agent.exception.GlobalExceptionHandler;
import org.example.ai_agent.service.UserService;
import org.example.ai_agent.service.admission.ChatAdmissionException;
import org.example.ai_agent.service.admission.GuestCreationAdmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("minimal")
class UserControllerValidationTest {

    @Mock
    private UserService userService;
    @Mock
    private GuestCreationAdmissionService guestCreationAdmissionService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new UserController(userService, guestCreationAdmissionService))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void register_shouldReturnValidationErrorWhenRequestBodyIsInvalid() {
        webTestClient.post()
                .uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username": "",
                          "password": "123",
                          "email": "invalid-email"
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.message").exists();
    }

    @Test
    void login_shouldReturnValidationErrorWhenPasswordIsBlank() {
        webTestClient.post()
                .uri("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username": "tester",
                          "password": ""
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.message").isEqualTo("密码不能为空");
    }

    @Test
    void register_shouldHideInternalErrorDetails() {
        when(userService.registerUser(any())).thenReturn(Mono.error(new RuntimeException("db stack trace")));

        webTestClient.post()
                .uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "username": "tester",
                          "password": "123456",
                          "email": "tester@example.com"
                        }
                        """)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.message").isEqualTo("注册失败，请稍后重试");
    }

    @Test
    void guestCreation_shouldReturnTooManyRequestsWhenAdmissionRejects() {
        when(guestCreationAdmissionService.admit(any()))
                .thenReturn(Mono.error(new ChatAdmissionException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "游客账号创建过于频繁，请稍后再试")));

        webTestClient.post()
                .uri("/api/users/guest")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectBody()
                .jsonPath("$.message").isEqualTo("游客账号创建过于频繁，请稍后再试");
    }
}
