package org.example.ai_agent.util;

public final class TokenRedisKeys {

    private TokenRedisKeys() {
    }

    public static String jti(String jti) {
        return "token:jti:" + jti;
    }

    public static String userJtis(Long userId) {
        return "token:user:" + userId + ":jtis";
    }

    public static String userRevoked(Long userId) {
        return "token:user-revoked:" + userId;
    }
}
