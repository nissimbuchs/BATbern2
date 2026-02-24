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

# Copy speaker-coordination-service module
COPY services/speaker-coordination-service/build.gradle services/speaker-coordination-service/gradlew* ./services/speaker-coordination-service/
COPY services/speaker-coordination-service/gradle ./services/speaker-coordination-service/gradle
COPY services/speaker-coordination-service/src ./services/speaker-coordination-service/src

# Build shared-kernel first (separate step to ensure it's fully available)
RUN gradle :shared-kernel:build --no-daemon -x test

# Build speaker-coordination-service subproject (compile tests but don't run them)
RUN gradle :services:speaker-coordination-service:build :services:speaker-coordination-service:compileTestJava --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -g 1001 -S spring && \
    adduser -S spring -u 1001

COPY --from=build --chown=spring:spring /workspace/services/speaker-coordination-service/build/libs/*.jar app.jar

# Switch to non-root user
USER spring

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=50.0", \
  "-XX:InitialRAMPercentage=35.0", \
  "-XX:+UseG1GC", \
  "-jar", "app.jar"]
