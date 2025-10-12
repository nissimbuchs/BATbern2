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

# Copy company-user-management-service module
COPY services/company-user-management-service/build.gradle services/company-user-management-service/gradlew* ./services/company-user-management-service/
COPY services/company-user-management-service/gradle ./services/company-user-management-service/gradle
COPY services/company-user-management-service/src ./services/company-user-management-service/src

# Build company-user-management-service subproject
RUN gradle :services:company-user-management-service:build --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

COPY --from=build /workspace/services/company-user-management-service/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
