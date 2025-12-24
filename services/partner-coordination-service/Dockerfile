# Build stage
FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace

# Copy root build configuration for multi-project build
COPY settings.gradle build.gradle ./

# Copy checkstyle configuration
COPY config ./config

# Copy gradle scripts for security scanning
COPY gradle ./gradle

# Copy shared-kernel module (dependency)
COPY shared-kernel/build.gradle ./shared-kernel/
COPY shared-kernel/src ./shared-kernel/src

# Copy OpenAPI specifications (required for openApiGenerate task)
COPY docs/api ./docs/api

# Copy partner-coordination-service module
COPY services/partner-coordination-service/build.gradle services/partner-coordination-service/gradlew* ./services/partner-coordination-service/
COPY services/partner-coordination-service/gradle ./services/partner-coordination-service/gradle
COPY services/partner-coordination-service/src ./services/partner-coordination-service/src

# Build shared-kernel first (separate step to ensure it's fully available)
RUN gradle :shared-kernel:build --no-daemon -x test

# Build partner-coordination-service subproject (compile tests but don't run them)
RUN gradle :services:partner-coordination-service:build :services:partner-coordination-service:compileTestJava --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -g 1001 -S spring && \
    adduser -S spring -u 1001

COPY --from=build --chown=spring:spring /workspace/services/partner-coordination-service/build/libs/*.jar app.jar

# Switch to non-root user
USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
