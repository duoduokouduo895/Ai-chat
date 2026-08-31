package org.example.ai_agent.config.database;

import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.example.ai_agent.repository.search.ConversationDocumentRepository;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.time.Duration;

@Configuration
@EnableR2dbcRepositories(
        basePackages = "org.example.ai_agent.repository",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = ConversationDocumentRepository.class
        )
)
public class R2dbcConfig extends AbstractR2dbcConfiguration {

    @Value("${spring.r2dbc.username}")
    private String username;

    @Value("${spring.r2dbc.password}")
    private String password;

    @Value("${spring.r2dbc.host}")
    private String host;

    @Value("${spring.r2dbc.port}")
    private int port;

    @Value("${spring.r2dbc.database}")
    private String database;

    @Value("${spring.r2dbc.pool.initial-size:2}")
    private int initialSize;

    @Value("${spring.r2dbc.pool.max-size:20}")
    private int maxSize;

    @Value("${spring.r2dbc.pool.max-idle-time:30m}")
    private Duration maxIdleTime;

    @Value("${spring.r2dbc.pool.max-life-time:60m}")
    private Duration maxLifeTime;

    @Value("${spring.r2dbc.pool.max-acquire-time:3s}")
    private Duration maxAcquireTime;

    @Value("${spring.r2dbc.pool.max-create-connection-time:3s}")
    private Duration maxCreateConnectionTime;

    @Value("${spring.r2dbc.pool.validation-query:SELECT 1}")
    private String validationQuery;

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
        MySqlConnectionConfiguration configuration = MySqlConnectionConfiguration.builder()
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .database(database)
                .sessionVariables(
                        "character_set_client=utf8mb4",
                        "character_set_connection=utf8mb4",
                        "character_set_results=utf8mb4"
                )
                .build();

        MySqlConnectionFactory connectionFactory = MySqlConnectionFactory.from(configuration);

        // 连接池大小由 profile 控制：local 避免预建连接，prod 保持较高吞吐。
        ConnectionPoolConfiguration poolConfiguration = ConnectionPoolConfiguration.builder(connectionFactory)
                .maxIdleTime(maxIdleTime)
                .maxLifeTime(maxLifeTime)
                .maxAcquireTime(maxAcquireTime)
                .maxCreateConnectionTime(maxCreateConnectionTime)
                .initialSize(initialSize)
                .maxSize(maxSize)
                .validationQuery(validationQuery)
                .build();

        return new ConnectionPool(poolConfiguration);
    }

}
