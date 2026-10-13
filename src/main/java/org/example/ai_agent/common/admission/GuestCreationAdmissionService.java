package org.example.ai_agent.common.admission;

import io.netty.util.NetUtil;
import org.example.ai_agent.auth.security.TokenRedisKeys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * 游客账号创建的前置防刷。
 * 只有 TCP 对端是本机或私网代理时才信任 Nginx 覆写的 X-Real-IP；
 * 公网直连和非法 Header 均回退到 TCP 对端地址。
 */
@Service
public class GuestCreationAdmissionService {

    private static final String REAL_IP_HEADER = "X-Real-IP";

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
        if (address == null) {
            return remoteAddress.getHostString();
        }

        if (isTrustedReverseProxy(address)) {
            String forwardedAddress = normalizeIpLiteral(
                    exchange.getRequest().getHeaders().getFirst(REAL_IP_HEADER));
            if (forwardedAddress != null) {
                return forwardedAddress;
            }
        }

        return address.getHostAddress();
    }

    private boolean isTrustedReverseProxy(InetAddress address) {
        return address.isLoopbackAddress() || address.isSiteLocalAddress();
    }

    private String normalizeIpLiteral(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.trim();
        if (!NetUtil.isValidIpV4Address(candidate) && !NetUtil.isValidIpV6Address(candidate)) {
            return null;
        }
        InetAddress address = NetUtil.createInetAddressFromIpAddressString(candidate);
        return address != null ? address.getHostAddress() : null;
    }
}
