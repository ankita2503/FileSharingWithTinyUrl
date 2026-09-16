# ---- build stage ----
FROM eclipse-temurin:22-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true   # warm dependency cache layer
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ---- runtime stage ----
FROM eclipse-temurin:22-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["java", "-jar", "app.jar"]