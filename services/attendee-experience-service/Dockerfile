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

# Copy attendee-experience-service module
COPY services/attendee-experience-service/build.gradle services/attendee-experience-service/gradlew* ./services/attendee-experience-service/
COPY services/attendee-experience-service/gradle ./services/attendee-experience-service/gradle
COPY services/attendee-experience-service/src ./services/attendee-experience-service/src

# Build shared-kernel first (separate step to ensure it's fully available)
RUN gradle :shared-kernel:build --no-daemon -x test

# Build attendee-experience-service subproject (compile tests but don't run them)
RUN gradle :services:attendee-experience-service:build :services:attendee-experience-service:compileTestJava --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -g 1001 -S spring && \
    adduser -S spring -u 1001

COPY --from=build --chown=spring:spring /workspace/services/attendee-experience-service/build/libs/*.jar app.jar

# Switch to non-root user
USER spring

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=40.0", \
  "-XX:InitialRAMPercentage=30.0", \
  "-XX:+UseG1GC", \
  "-jar", "app.jar"]
