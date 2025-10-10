# Build stage
FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace

# Copy root settings.gradle for multi-project build
COPY settings.gradle ./

# Copy shared-kernel module (dependency)
COPY shared-kernel/build.gradle ./shared-kernel/
COPY shared-kernel/src ./shared-kernel/src

# Copy speaker-coordination-service module
COPY services/speaker-coordination-service/build.gradle services/speaker-coordination-service/gradlew* ./services/speaker-coordination-service/
COPY services/speaker-coordination-service/gradle ./services/speaker-coordination-service/gradle
COPY services/speaker-coordination-service/src ./services/speaker-coordination-service/src

# Build speaker-coordination-service subproject
RUN gradle :services:speaker-coordination-service:build --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/speaker-coordination-service/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
