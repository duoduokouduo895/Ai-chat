package org.example.ai_agent.config.database;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableReactiveElasticsearchRepositories; // Changed

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Configuration
@EnableReactiveElasticsearchRepositories(basePackages = "org.example.ai_agent.repository") // Changed
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:http://112.74.98.163:9200}")
    private String elasticsearchUri;

    @Value("${spring.elasticsearch.connection-timeout:10000}")
    private int connectionTimeout;

    @Value("${spring.elasticsearch.socket-timeout:60000}")
    private int socketTimeout;

    @Override
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .connectedTo(elasticsearchUri.replace("http://", "").replace("https://", ""))
                .withConnectTimeout(Duration.ofMillis(connectionTimeout))
                .withSocketTimeout(Duration.ofMillis(socketTimeout))
                .build();
    }

    @PostConstruct
    public void testConnection() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectionTimeout))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(elasticsearchUri))
                    .timeout(Duration.ofMillis(socketTimeout))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                System.out.println("✅ Elasticsearch连接成功: " + elasticsearchUri);
                System.out.println("📊 ES服务器信息: " + response.body());
            } else {
                System.err.println("⚠️ Elasticsearch连接异常，状态码: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("❌ Elasticsearch连接失败: " + e.getMessage());
            System.err.println("🔧 请检查ES服务器地址: " + elasticsearchUri);
            System.err.println("💡 建议: 确保ES服务正在运行且网络可达");
        }
    }
    
    // 为KnowledgeBaseConfig提供获取URI的方法
    public String getElasticsearchUri() {
        return elasticsearchUri;
    }
    
    public int getConnectionTimeout() {
        return connectionTimeout;
    }
    
    public int getSocketTimeout() {
        return socketTimeout;
    }
}