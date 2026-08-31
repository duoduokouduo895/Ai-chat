package org.example.ai_agent.config.ai.http;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;

@Slf4j
public class Utf8SpringRestClient implements HttpClient {

    private static final Utf8ServerSentEventParser UTF_8_SSE_PARSER = new Utf8ServerSentEventParser();

    private final RestClient delegate;
    private final Executor streamingRequestExecutor;

    public Utf8SpringRestClient(Duration connectTimeout, Duration readTimeout, Executor streamingRequestExecutor) {
        HttpClientSettings settings = HttpClientSettings.defaults();
        if (connectTimeout != null) {
            settings = settings.withConnectTimeout(connectTimeout);
        }
        if (readTimeout != null) {
            settings = settings.withReadTimeout(readTimeout);
        }

        ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        this.delegate = RestClient.builder()
                .requestFactory(clientHttpRequestFactory)
                .build();
        this.streamingRequestExecutor = streamingRequestExecutor;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException {
        try {
            ResponseEntity<String> responseEntity = toSpringRestClientRequest(request)
                    .retrieve()
                    .toEntity(String.class);

            return SuccessfulHttpResponse.builder()
                    .statusCode(responseEntity.getStatusCode().value())
                    .headers(toHeaderMap(responseEntity.getHeaders()))
                    .body(responseEntity.getBody())
                    .build();
        } catch (RestClientResponseException e) {
            throw new HttpException(e.getStatusCode().value(), e.getMessage());
        } catch (Exception e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new TimeoutException(e);
            }
            throw e;
        }
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventListener listener) {
        execute(request, UTF_8_SSE_PARSER, listener);
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        streamingRequestExecutor.execute(() -> {
            try {
                toSpringRestClientRequest(request)
                        .exchange((springRequest, springResponse) -> {
                            int statusCode = springResponse.getStatusCode().value();

                            if (!springResponse.getStatusCode().is2xxSuccessful()) {
                                String body = springResponse.bodyTo(String.class);
                                log.warn("AI provider streaming request failed: target={}, status={}",
                                        safeTarget(request.url()), statusCode);
                                HttpException exception = new HttpException(statusCode, body);
                                ignoringExceptions(() -> listener.onError(exception));
                                return null;
                            }

                            SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                                    .statusCode(statusCode)
                                    .headers(toHeaderMap(springResponse.getHeaders()))
                                    .build();
                            ignoringExceptions(() -> listener.onOpen(response));

                            try (InputStream inputStream = springResponse.getBody()) {
                                UTF_8_SSE_PARSER.parse(inputStream, listener);
                                ignoringExceptions(listener::onClose);
                            }

                            return null;
                        });
            } catch (Exception e) {
                if (isCancellationException(e)) {
                    log.debug("流式大模型调用已被下游取消，成功断开底层网络连接并静默退出。");
                    return;
                }
                log.warn("AI provider streaming request error: target={}, errorType={}",
                        safeTarget(request.url()), e.getClass().getSimpleName());
                if (e.getCause() instanceof SocketTimeoutException) {
                    ignoringExceptions(() -> listener.onError(new TimeoutException(e)));
                } else {
                    ignoringExceptions(() -> listener.onError(e));
                }
            }
        });
    }

    private boolean isCancellationException(Throwable e) {
        if (e == null) {
            return false;
        }
        if ("StreamCancelledException".equals(e.getMessage()) ||
            (e.getCause() != null && "StreamCancelledException".equals(e.getCause().getMessage()))) {
            return true;
        }
        return isCancellationException(e.getCause());
    }

    private RestClient.RequestBodySpec toSpringRestClientRequest(HttpRequest request) {
        RestClient.RequestBodySpec requestBodySpec = delegate
                .method(org.springframework.http.HttpMethod.valueOf(request.method().name()))
                .uri(request.url())
                .headers(httpHeaders -> httpHeaders.putAll(request.headers()));

        if (request.body() != null) {
            requestBodySpec.body(request.body());
        }

        return requestBodySpec;
    }

    private String safeTarget(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() + uri.getPath();
        } catch (Exception ignored) {
            return "<invalid-url>";
        }
    }

    private Map<String, List<String>> toHeaderMap(org.springframework.http.HttpHeaders headers) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        headers.forEach((name, values) -> result.put(name, List.copyOf(values)));
        return result;
    }
}
