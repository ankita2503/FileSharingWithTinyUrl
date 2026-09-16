# ---- build ----
FROM eclipse-temurin:22-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ---- MinIO binary source ----
# dl.min.io returns 410 since Sept 2026 (community edition archived).
# quay.io still serves the images, so lift the binary out of one.
FROM quay.io/minio/minio:RELEASE.2025-02-18T16-25-55Z AS minio

# ---- runtime ----
FROM eclipse-temurin:22-jre-alpine
RUN apk add --no-cache curl bash
COPY --from=minio /usr/bin/minio /usr/local/bin/minio

RUN adduser -D -u 1000 app && mkdir -p /data/minio && chown -R app:app /data

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh && chown -R app:app /app

USER app
EXPOSE 7860
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC"
ENTRYPOINT ["/app/entrypoint.sh"]