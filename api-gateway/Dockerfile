# Build stage
FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace

# Copy root settings.gradle for multi-project build
COPY settings.gradle ./

# Copy shared-kernel module (dependency)
COPY shared-kernel/build.gradle ./shared-kernel/
COPY shared-kernel/src ./shared-kernel/src

# Copy api-gateway module
COPY api-gateway/build.gradle api-gateway/gradlew* ./api-gateway/
COPY api-gateway/gradle ./api-gateway/gradle
COPY api-gateway/src ./api-gateway/src

# Build api-gateway subproject
RUN gradle :api-gateway:build --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/api-gateway/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
