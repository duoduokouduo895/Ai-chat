package org.example.ai_agent.service.admission;

import org.example.ai_agent.config.security.ChatAdmissionProperties;
import org.example.ai_agent.util.TokenRedisKeys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/** 游客账号创建的前置防刷。默认只信任 TCP 对端地址，不信任可伪造的转发头。 */
@Service
public class GuestCreationAdmissionService {

    private final RedisAdmissionStore redisAdmissionStore;
    private final ChatAdmissionProperties properties;

    public GuestCreationAdmissionService(RedisAdmissionStore redisAdmissionStore,
                                         ChatAdmissionProperties properties) {
        this.redisAdmissionStore = redisAdmissionStore;
        this.properties = properties;
    }

    public Mono<Void> admit(ServerWebExchange exchange) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }
        ChatAdmissionProperties.GuestCreation guestCreation = properties.getGuestCreation();
        return redisAdmissionStore.tryConsume(TokenRedisKeys.guestCreation(clientAddress(exchange)),
                        guestCreation.requestLimit(), guestCreation.requestWindow())
                .flatMap(accepted -> accepted ? Mono.empty() : Mono.error(
                        new ChatAdmissionException(HttpStatus.TOO_MANY_REQUESTS,
                                "游客账号创建过于频繁，请稍后再试")));
    }

    private String clientAddress(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null) {
            return "unknown";
        }
        InetAddress address = remoteAddress.getAddress();
        return address != null ? address.getHostAddress() : remoteAddress.getHostString();
    }
}
