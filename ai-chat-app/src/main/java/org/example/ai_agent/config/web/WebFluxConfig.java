package org.example.ai_agent.config.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux配置类
 *
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // 配置multipart和其他编解码器的大小限制
        configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024); // 50MB

        // Spring Boot 3.x会自动配置multipart解析器，我们只需要设置大小限制
        // 具体的multipart配置通过application.properties来设置
    }
}
