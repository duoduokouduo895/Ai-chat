package org.example.ai_agent.llm.http;

import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;

/**
 * LangChain4j 默认 SSE parser 未显式指定 charset，在 Windows 下会退回系统默认编码。
 * 这里强制按 UTF-8 解析，避免流式中文被错误解码。
 */
public class Utf8ServerSentEventParser implements ServerSentEventParser {

    @Override
    public void parse(InputStream httpResponseBody, ServerSentEventListener listener) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(httpResponseBody, StandardCharsets.UTF_8))) {

            String event = null;
            StringBuilder data = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    emitEvent(listener, event, data);
                    event = null;
                    data.setLength(0);
                    continue;
                }

                if (line.startsWith("event:")) {
                    event = line.substring("event:".length()).trim();
                    continue;
                }

                if (line.startsWith("data:")) {
                    String content = line.substring("data:".length());
                    if (!data.isEmpty()) {
                        data.append('\n');
                    }
                    data.append(content.trim());
                }
            }

            emitEvent(listener, event, data);
        } catch (IOException e) {
            ignoringExceptions(() -> listener.onError(e));
        }
    }

    private void emitEvent(ServerSentEventListener listener, String event, StringBuilder data) {
        if (data.isEmpty()) {
            return;
        }

        ServerSentEvent sse = new ServerSentEvent(event, data.toString());
        listener.onEvent(sse);
    }
}
