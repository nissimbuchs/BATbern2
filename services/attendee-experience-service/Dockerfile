# Build stage
FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace

# Copy root settings.gradle for multi-project build
COPY settings.gradle ./

# Copy shared-kernel module (dependency)
COPY shared-kernel/build.gradle ./shared-kernel/
COPY shared-kernel/src ./shared-kernel/src

# Copy attendee-experience-service module
COPY services/attendee-experience-service/build.gradle services/attendee-experience-service/gradlew* ./services/attendee-experience-service/
COPY services/attendee-experience-service/gradle ./services/attendee-experience-service/gradle
COPY services/attendee-experience-service/src ./services/attendee-experience-service/src

# Build attendee-experience-service subproject
RUN gradle :services:attendee-experience-service:build --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/attendee-experience-service/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
