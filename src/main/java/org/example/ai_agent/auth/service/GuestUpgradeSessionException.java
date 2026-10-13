package org.example.ai_agent.auth.service;

/** 游客资料已升级，但无法安全完成旧会话令牌轮换时使用的受控异常。 */
public class GuestUpgradeSessionException extends RuntimeException {

    public GuestUpgradeSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
