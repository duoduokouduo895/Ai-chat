package org.example.ai_agent.config.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 配置类
 * <p>
 * 该类使用 @ConfigurationProperties 注解从应用的配置文件（例如 application.properties 或外部配置文件）
 * 中加载所有以 "jwt" 为前缀的配置项。
 * </p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    /**
     * 用于签名和验证JWT的密钥。
     * 这是一个必需的配置，必须足够强大以确保安全。
     */
    private String secret;
    /**
     * JWT 的过期时间，单位为毫秒。
     * 例如: 86400000 对应24小时。
     */
    private Long expiration;

    /**
     * JWT 刷新窗口，单位为毫秒。
     * 当一个 token 的剩余有效期小于这个值时，系统可以为其签发一个新的 token。
     * 例如: 300000 对应5分钟。
     */
    private Long refreshWindow;
    private String header;
    private String prefix;
}
