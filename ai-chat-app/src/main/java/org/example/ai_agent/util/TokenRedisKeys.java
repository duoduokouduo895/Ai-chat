package org.example.ai_agent.util;

public final class TokenRedisKeys {

    private TokenRedisKeys() {
    }

    public static String jti(String jti) {
        return "token:jti:" + jti;
    }

    /**
     * One-time refresh-session key. Its lifetime extends through the configured
     * refresh grace window, unlike the access-token JTI key.
     */
    public static String refreshJti(String jti) {
        return "token:refresh-jti:" + jti;
    }

    public static String userJtis(Long userId) {
        return "token:user:" + userId + ":jtis";
    }

    public static String userRevoked(Long userId) {
        return "token:user-revoked:" + userId;
    }

    public static String chatConcurrencyGlobal() {
        return "admission:chat:concurrency:global";
    }

    public static String chatConcurrencyUser(Long userId) {
        return "admission:chat:concurrency:user:" + userId;
    }

    public static String chatGuestQuota(Long userId) {
        return "admission:chat:guest-quota:" + userId;
    }

    public static String guestCreation(String clientAddress) {
        return "admission:guest:create:" + clientAddress;
    }
}
