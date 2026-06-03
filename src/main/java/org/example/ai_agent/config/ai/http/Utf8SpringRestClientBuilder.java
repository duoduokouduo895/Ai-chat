package org.example.ai_agent.config.ai.http;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;

import java.time.Duration;
import java.util.concurrent.Executor;

public class Utf8SpringRestClientBuilder implements HttpClientBuilder {

    private Duration connectTimeout;
    private Duration readTimeout;
    private final Executor streamingRequestExecutor;

    public Utf8SpringRestClientBuilder(Executor streamingRequestExecutor) {
        this.streamingRequestExecutor = streamingRequestExecutor;
    }

    @Override
    public Duration connectTimeout() {
        return connectTimeout;
    }

    @Override
    public HttpClientBuilder connectTimeout(Duration timeout) {
        this.connectTimeout = timeout;
        return this;
    }

    @Override
    public Duration readTimeout() {
        return readTimeout;
    }

    @Override
    public HttpClientBuilder readTimeout(Duration timeout) {
        this.readTimeout = timeout;
        return this;
    }

    @Override
    public HttpClient build() {
        return new Utf8SpringRestClient(connectTimeout, readTimeout, streamingRequestExecutor);
    }
}
