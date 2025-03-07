# Dockerfile for MCP Chatbot application
FROM gradle:7.4.2-jdk17 AS build

WORKDIR /app

# Copy build configuration files
COPY build.gradle.kts settings.gradle.kts gradlew gradlew.bat ./
COPY gradle ./gradle

# Download dependencies
RUN gradle dependencies --no-daemon

# Copy source code
COPY src ./src

# Build the application
RUN gradle build --no-daemon

# Runtime image
FROM openjdk:17-slim

WORKDIR /app

# Copy the built application from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Copy static resources
COPY src/main/resources ./resources

# Create config directory and sample .env file
RUN mkdir -p ./config
COPY config/sample.env ./config/.env

# Expose the port
EXPOSE 8080

# Set the entrypoint
ENTRYPOINT ["java", "-jar", "app.jar"]