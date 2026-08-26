# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B test && mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="HCOP BFF" \
      org.opencontainers.image.description="Backend-for-frontend: proxy de sesión y streaming hacia el backend HCOP JP" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.source="https://github.com/Marcolyto/HCOP_JP"

RUN groupadd --system --gid 10001 hcop \
    && useradd --system --uid 10001 --gid hcop --home-dir /opt/hcop --shell /usr/sbin/nologin hcop

WORKDIR /opt/hcop
COPY --chown=hcop:hcop --from=build /workspace/target/hcop-bff.jar ./app.jar

USER hcop
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=8 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]

ENTRYPOINT ["java", "-jar", "/opt/hcop/app.jar"]
