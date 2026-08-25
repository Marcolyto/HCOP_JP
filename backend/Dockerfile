# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests dependency:go-offline
COPY src ./src
COPY runtime/catalogs ./runtime/catalogs
RUN --mount=type=cache,target=/root/.m2 mvn -B test && mvn -B -DskipTests package
# Defensa de empaquetado: el backend es API pura, nunca debe llevar estáticos del frontend.
RUN ! jar tf target/hcop-jp.jar | grep -q '^BOOT-INF/classes/static/'

FROM eclipse-temurin:21-jre-jammy AS runtime
ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="HCOP JP Backend" \
      org.opencontainers.image.description="API de historia clínica oncológica y Hospital de Día" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.source="https://github.com/Marcolyto/HCOP_JP"

RUN groupadd --system --gid 10001 hcop \
    && useradd --system --uid 10001 --gid hcop --home-dir /opt/hcop --shell /usr/sbin/nologin hcop

WORKDIR /opt/hcop
COPY --chown=hcop:hcop --from=build /workspace/target/hcop-jp.jar ./app.jar
COPY --chown=hcop:hcop runtime/catalogs ./runtime/catalogs
RUN mkdir -p ./runtime/storage && chown hcop:hcop ./runtime ./runtime/storage

USER hcop
EXPOSE 5180
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8" \
    HCOP_RUNTIME_ROOT=/opt/hcop/runtime \
    HCOP_CATALOG_ROOT=/opt/hcop/runtime/catalogs \
    HCOP_STORAGE_ROOT=/opt/hcop/runtime/storage

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=8 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/5180 && printf 'GET /actuator/health HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]

ENTRYPOINT ["java", "-jar", "/opt/hcop/app.jar"]
