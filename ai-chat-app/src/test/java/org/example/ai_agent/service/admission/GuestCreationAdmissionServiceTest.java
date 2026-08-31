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
    void admit_shouldUseNginxRealIpWhenDirectPeerIsTrustedProxy() {
        ChatAdmissionProperties properties = new ChatAdmissionProperties();
        GuestCreationAdmissionService service = new GuestCreationAdmissionService(redisAdmissionStore, properties);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/users/guest")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                .header("X-Real-IP", "198.51.100.9")
                .header("X-Forwarded-For", "203.0.113.10, 198.51.100.9")
                .build();

        when(redisAdmissionStore.tryConsume(eq(TokenRedisKeys.guestCreation("198.51.100.9")), anyInt(), any()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.admit(MockServerWebExchange.from(request))).verifyComplete();
    }

    @Test
    void admit_shouldIgnoreRealIpWhenDirectPeerIsPublic() {
        ChatAdmissionProperties properties = new ChatAdmissionProperties();
        GuestCreationAdmissionService service = new GuestCreationAdmissionService(redisAdmissionStore, properties);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/users/guest")
                .remoteAddress(new InetSocketAddress("203.0.113.7", 12345))
                .header("X-Real-IP", "198.51.100.9")
                .build();

        when(redisAdmissionStore.tryConsume(eq(TokenRedisKeys.guestCreation("203.0.113.7")), anyInt(), any()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.admit(MockServerWebExchange.from(request))).verifyComplete();
    }

    @Test
    void admit_shouldFallBackToPeerWhenTrustedProxySendsInvalidRealIp() {
        ChatAdmissionProperties properties = new ChatAdmissionProperties();
        GuestCreationAdmissionService service = new GuestCreationAdmissionService(redisAdmissionStore, properties);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/users/guest")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 12345))
                .header("X-Real-IP", "not-an-ip")
                .build();

        when(redisAdmissionStore.tryConsume(eq(TokenRedisKeys.guestCreation("10.0.0.1")), anyInt(), any()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.admit(MockServerWebExchange.from(request))).verifyComplete();
    }
}
