# Build stage
FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace

# Copy root settings.gradle for multi-project build
COPY settings.gradle ./

# Copy checkstyle configuration
COPY config ./config

# Copy shared-kernel module (dependency)
COPY shared-kernel/build.gradle ./shared-kernel/
COPY shared-kernel/src ./shared-kernel/src

# Copy partner-coordination-service module
COPY services/partner-coordination-service/build.gradle services/partner-coordination-service/gradlew* ./services/partner-coordination-service/
COPY services/partner-coordination-service/gradle ./services/partner-coordination-service/gradle
COPY services/partner-coordination-service/src ./services/partner-coordination-service/src

# Build partner-coordination-service subproject
RUN gradle :services:partner-coordination-service:build --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

COPY --from=build /workspace/services/partner-coordination-service/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
