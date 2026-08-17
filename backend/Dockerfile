# syntax=docker/dockerfile:1

# ---- Build stage -------------------------------------------------------------
# Dependencies resolve in their own layer so source-only changes skip the
# download on every rebuild.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# curl backs the container HEALTHCHECK and is not present in the JRE base image.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged account so a container compromise does not grant root.
RUN groupadd --system --gid 1001 careflow \
    && useradd --system --uid 1001 --gid careflow --home /app --shell /usr/sbin/nologin careflow \
    && mkdir -p /app/logs \
    && chown -R careflow:careflow /app

WORKDIR /app

COPY --from=build --chown=careflow:careflow /build/target/careflow-backend.jar app.jar

# Build metadata, surfaced by /api/system/version so the running image is
# identifiable at a glance.
ARG APP_VERSION=1.0.0
ARG GIT_COMMIT=unknown
ARG DEPLOYED_AT=unknown

ENV APP_VERSION=${APP_VERSION} \
    GIT_COMMIT=${GIT_COMMIT} \
    DEPLOYED_AT=${DEPLOYED_AT} \
    SERVER_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError"

USER careflow

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["sh", "-c", "curl -fsS http://localhost:${SERVER_PORT}/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
