package org.example.ai_agent.config.database;

import org.example.ai_agent.repository.search.ConversationDocumentRepository;
import org.example.ai_agent.repository.search.SearchHistoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableReactiveElasticsearchRepositories;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;

@Configuration
@EnableReactiveElasticsearchRepositories(
        basePackageClasses = ConversationDocumentRepository.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SearchHistoryRepository.class
        )
)
public class ElasticsearchConfig extends ReactiveElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris}")
    private String elasticsearchUri;

    @Value("${spring.elasticsearch.connection-timeout:10000}")
    private int connectionTimeout;

    @Value("${spring.elasticsearch.socket-timeout:60000}")
    private int socketTimeout;

    @Value("${spring.elasticsearch.username:}")
    private String username;

    @Value("${spring.elasticsearch.password:}")
    private String password;

    @Override
    public ClientConfiguration clientConfiguration() {
        URI uri = validatedUri();
        var endpointBuilder = ClientConfiguration.builder().connectedTo(uri.getRawAuthority());
        ClientConfiguration.TerminalClientConfigurationBuilder builder =
                "https".equalsIgnoreCase(uri.getScheme()) ? endpointBuilder.usingSsl() : endpointBuilder;
        builder.withConnectTimeout(Duration.ofMillis(connectionTimeout))
                .withSocketTimeout(Duration.ofMillis(socketTimeout));
        validateCredentials();
        if (hasCredentials()) {
            builder.withBasicAuth(username, password);
        }
        return builder.build();
    }

    public String getElasticsearchUri() {
        return elasticsearchUri;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public boolean hasCredentials() {
        return StringUtils.hasText(username) && StringUtils.hasText(password);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    private URI validatedUri() {
        if (!StringUtils.hasText(elasticsearchUri)) {
            throw new IllegalStateException("spring.elasticsearch.uris 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(elasticsearchUri.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("spring.elasticsearch.uris 格式非法", exception);
        }
        boolean supportedScheme = "http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme());
        boolean rootPath = uri.getRawPath() == null || uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath());
        if (!supportedScheme || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null
                || !rootPath || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException("spring.elasticsearch.uris 必须是无凭据、无路径的 http(s) endpoint");
        }
        return uri;
    }

    private void validateCredentials() {
        if (StringUtils.hasText(username) != StringUtils.hasText(password)) {
            throw new IllegalStateException("Elasticsearch username 与 password 必须同时配置或同时留空");
        }
    }
}
