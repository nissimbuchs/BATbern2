# syntax=docker/dockerfile:1.4
# Enable BuildKit features for better caching

# Build stage
FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace

# Copy root settings.gradle for multi-project build
COPY settings.gradle ./

# Copy checkstyle configuration
COPY config ./config

# Copy gradle scripts for security scanning
COPY gradle ./gradle

# Copy shared-kernel build file ONLY (for dependency resolution)
COPY shared-kernel/build.gradle ./shared-kernel/

# Copy event-management-service build files ONLY (for dependency resolution)
COPY services/event-management-service/build.gradle services/event-management-service/gradlew* ./services/event-management-service/
COPY services/event-management-service/gradle ./services/event-management-service/gradle

# Download dependencies FIRST (cached layer if build.gradle unchanged)
# Use BuildKit cache mount to persist Gradle cache between builds
RUN --mount=type=cache,target=/root/.gradle \
    gradle :services:event-management-service:dependencies --no-daemon || true

# Now copy source code (changes more frequently)
COPY shared-kernel/src ./shared-kernel/src
COPY docs/api ./docs/api
COPY services/event-management-service/src ./services/event-management-service/src

# Build event-management-service subproject (compile tests but don't run them)
# Use BuildKit cache mount for Gradle cache
RUN --mount=type=cache,target=/root/.gradle \
    gradle :services:event-management-service:build :services:event-management-service:compileTestJava --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

COPY --from=build /workspace/services/event-management-service/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
