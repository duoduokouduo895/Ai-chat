package org.example.ai_agent.service.admission;

import org.example.ai_agent.config.security.ChatAdmissionProperties;
import org.example.ai_agent.util.TokenRedisKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestCreationAdmissionServiceTest {

    @Mock
    private RedisAdmissionStore redisAdmissionStore;

    @Test
    void admit_shouldUseRemoteAddressInsteadOfForwardedHeader() {
        ChatAdmissionProperties properties = new ChatAdmissionProperties();
        GuestCreationAdmissionService service = new GuestCreationAdmissionService(redisAdmissionStore, properties);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/users/guest")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                .header("X-Forwarded-For", "198.51.100.9")
                .build();

        when(redisAdmissionStore.tryConsume(eq(TokenRedisKeys.guestCreation("127.0.0.1")), anyInt(), any()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.admit(MockServerWebExchange.from(request))).verifyComplete();
    }
}
