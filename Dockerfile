FROM maven:3.9.16-eclipse-temurin-17@sha256:da66ff6a39526253d98bf5d01fefcd0142bce05e9e3f165345149d207f9024c9 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp dependency:copy \
    -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:2.29.0 \
    -DoutputDirectory=/otel

COPY src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:17-jre-noble@sha256:51a5b49f064164c5b47358a261645945a13a6b3f3622d41aca89a7421fc264cd

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 aichat \
    && useradd --system --uid 10001 --gid aichat --home-dir /opt/aichat --shell /usr/sbin/nologin aichat

WORKDIR /opt/aichat
COPY --from=build /workspace/target/Ai_Agent-*.jar /opt/aichat/application.jar
COPY --from=build /otel/opentelemetry-javaagent-2.29.0.jar /opt/otel/opentelemetry-javaagent.jar

RUN chown -R aichat:aichat /opt/aichat

USER 10001:10001
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/otel/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    OTEL_SERVICE_NAME="ai-chat" \
    OTEL_METRICS_EXPORTER="none" \
    OTEL_LOGS_EXPORTER="none" \
    OTEL_TRACES_EXPORTER="otlp" \
    OTEL_EXPORTER_OTLP_PROTOCOL="http/protobuf" \
    OTEL_EXPORTER_OTLP_ENDPOINT="http://alloy:4318"

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/opt/aichat/application.jar"]
