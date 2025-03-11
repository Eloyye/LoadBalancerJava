# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-23 AS build

# Set working directory
WORKDIR /app

# Copy the POM file first to leverage Docker cache
COPY pom.xml .

# Download all dependencies
# This is a separate step to leverage Docker cache
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:23-jre-alpine

# Set working directory
WORKDIR /app

# Create a non-root user to run the application
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the JAR file from the build stage
COPY --from=build /app/target/lobalancer-1.0-SNAPSHOT.jar .

# Copy any configuration files if needed
# COPY --from=build /app/src/main/resources/ ./config/

# Set proper permissions
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose the port the application runs on
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
  CMD wget -q --spider http://localhost:80/health || exit 1

# Command to run the application
ENTRYPOINT ["java", "-jar", "lobalancer-1.0-SNAPSHOT.jar"]