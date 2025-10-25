# Build stage
FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace

# Copy root settings.gradle for multi-project build
COPY settings.gradle ./

# Copy checkstyle configuration
COPY config ./config

# Copy gradle scripts for security scanning
COPY gradle ./gradle

# Copy shared-kernel module (dependency)
COPY shared-kernel/build.gradle ./shared-kernel/
COPY shared-kernel/src ./shared-kernel/src

# Copy OpenAPI specifications (required for openApiGenerate task)
COPY docs/api ./docs/api

# Copy event-management-service module
COPY services/event-management-service/build.gradle services/event-management-service/gradlew* ./services/event-management-service/
COPY services/event-management-service/gradle ./services/event-management-service/gradle
COPY services/event-management-service/src ./services/event-management-service/src

# Build event-management-service subproject (compile tests but don't run them)
RUN gradle :services:event-management-service:build :services:event-management-service:compileTestJava --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

COPY --from=build /workspace/services/event-management-service/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
