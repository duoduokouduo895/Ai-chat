package org.example.ai_agent.config.database;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 数据库版本管理配置
 *
 * <p>本项目使用 R2DBC（响应式）作为业务数据源，而 Flyway 仅支持传统 JDBC。
 * 因此这里从 Nacos 已有的 spring.r2dbc.* 属性推导出 JDBC 连接字符串，
 * 用于启动期执行迁移脚本，不影响运行时业务链路。</p>
 *
 * <p>迁移脚本位于 classpath:db/migration/ 目录下，遵循 Flyway 命名规范：
 * V{version}__{description}.sql</p>
 *
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Configuration
@EnableConfigurationProperties(FlywayProperties.class)
@ConditionalOnProperty(prefix = "app.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

    @Value("${spring.r2dbc.host}")
    private String host;

    @Value("${spring.r2dbc.port}")
    private int port;

    @Value("${spring.r2dbc.database}")
    private String database;

    @Value("${spring.r2dbc.username}")
    private String username;

    @Value("${spring.r2dbc.password}")
    private String password;

    /**
     * 创建并配置 Flyway 实例
     *
     * <p>从 R2DBC 属性推导 JDBC URL，baseline-on-migrate 设为 true
     * 以兼容已有生产数据库（不会对已有表重复执行建表语句）。</p>
     *
     * @return 配置完毕的 Flyway 实例
     */
    @Bean(initMethod = "migrate")
    public Flyway flyway() {
        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
                host, port, database);

        return Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                // 迁移脚本位置
                .locations("classpath:db/migration")
                // 兼容已有数据库：首次运行时标记现有 schema 为 baseline（版本 1）
                .baselineOnMigrate(true)
                .baselineVersion("0")
                // 编码
                .encoding("UTF-8")
                // 允许乱序迁移（团队协作时多分支可能产生不连续版本号）
                .outOfOrder(true)
                .load();
    }
}
