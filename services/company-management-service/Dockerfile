# Build stage
FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace

# Copy root settings.gradle for multi-project build
COPY settings.gradle ./

# Copy shared-kernel module (dependency)
COPY shared-kernel/build.gradle ./shared-kernel/
COPY shared-kernel/src ./shared-kernel/src

# Copy company-management-service module
COPY services/company-management-service/build.gradle services/company-management-service/gradlew* ./services/company-management-service/
COPY services/company-management-service/gradle ./services/company-management-service/gradle
COPY services/company-management-service/src ./services/company-management-service/src

# Build company-management-service subproject
RUN gradle :services:company-management-service:build --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/company-management-service/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
