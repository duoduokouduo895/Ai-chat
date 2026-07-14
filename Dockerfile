FROM maven:3.9.16-eclipse-temurin-21@sha256:0bef3f122ac7c0928b2fbcdf444f98cbd2db0ef7a1193bfa121cd70fa22221ef AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp dependency:copy \
    -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:2.29.0 \
    -DoutputDirectory=/otel

COPY compose.cloud.yaml ./compose.cloud.yaml
COPY deploy ./deploy
COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:21.0.11_10-jre-noble@sha256:c978ac6dd6aa90ec21935eb6c848f50ebf9b783160eefdd6accd8d2fd9ebeef1

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 aichat \
    && useradd --system --uid 10001 --gid aichat --home-dir /opt/aichat --shell /usr/sbin/nologin aichat

WORKDIR /opt/aichat
COPY --from=build /workspace/target/Ai_Agent-*.jar /opt/aichat/application.jar
COPY --from=build /otel/opentelemetry-javaagent-2.29.0.jar /opt/otel/opentelemetry-javaagent.jar
COPY deploy/docker-entrypoint.sh /opt/aichat/bin/docker-entrypoint.sh

RUN chown -R aichat:aichat /opt/aichat \
    && chmod 0555 /opt/aichat/bin/docker-entrypoint.sh

USER 10001:10001
EXPOSE 8080 9091

ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/otel/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    OTEL_SERVICE_NAME="ai-chat" \
    OTEL_METRICS_EXPORTER="none" \
    OTEL_LOGS_EXPORTER="none" \
    OTEL_TRACES_EXPORTER="otlp" \
    OTEL_EXPORTER_OTLP_PROTOCOL="http/protobuf" \
    OTEL_EXPORTER_OTLP_ENDPOINT="http://alloy:4318"

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD curl --fail --silent http://localhost:9091/actuator/health || exit 1

ENTRYPOINT ["/opt/aichat/bin/docker-entrypoint.sh"]
