## Build stage
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Only copy Gradle files first to leverage caching
COPY build.gradle settings.gradle /app/
COPY gradle /app/gradle

# Download dependencies (cached)
RUN gradle --no-daemon dependencies || true

# Now copy the source
COPY . /app

# Build the fat jar (tests skipped for faster builds)
RUN gradle --no-daemon clean shadowJar -x test


## Runtime stage with Playwright support
# Includes Java 17 and Playwright browsers/libs for JS-rendered docs
FROM mcr.microsoft.com/playwright/java:v1.41.2-jammy

ENV JAVA_OPTS=""
ENV SPRING_PROFILES_ACTIVE=default
ENV SERVER_PORT=8080
ENV PLAYWRIGHT_BROWSERS_PATH=0

WORKDIR /app

# Copy built jar from the builder stage
COPY --from=builder /app/build/libs/api-docs-scraper-1.0.0.jar /app/app.jar

EXPOSE 8080

# Health info (optional)
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=5 \
  CMD curl -f http://localhost:8080/api/v1/scraper/health || exit 1

ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --server.port=${SERVER_PORT}"]
