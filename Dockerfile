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

# Build the Spring Boot executable jar (tests skipped for faster builds)
RUN gradle --no-daemon clean bootJar -x test


## Runtime stage with Playwright support
# Includes Java 17 and Playwright browsers/libs for JS-rendered docs
FROM mcr.microsoft.com/playwright/java:v1.41.2-jammy

ENV JAVA_OPTS=""
ENV SPRING_PROFILES_ACTIVE=default
ENV SERVER_PORT=8080
ENV PLAYWRIGHT_BROWSERS_PATH=0

WORKDIR /app

# Copy built Spring Boot executable jar from the builder stage
# bootJar creates: api-docs-scraper-1.0.0.jar (Spring Boot executable jar)
COPY --from=builder /app/build/libs/api-docs-scraper-1.0.0.jar /app/app.jar

# Pre-extract Playwright driver to avoid JAR extraction issues at runtime
# Create a directory for Playwright driver and extract it from the JAR
RUN mkdir -p /app/.playwright/driver && \
    cd /tmp && \
    # Extract Playwright JAR from Spring Boot JAR
    unzip -q -o /app/app.jar "BOOT-INF/lib/playwright-*.jar" -d /tmp/playwright-extract || true && \
    # Find the Playwright JAR
    PLAYWRIGHT_JAR=$(find /tmp/playwright-extract -name "playwright-*.jar" | head -1) && \
    if [ -n "$PLAYWRIGHT_JAR" ] && [ -f "$PLAYWRIGHT_JAR" ]; then \
        # Extract driver from Playwright JAR
        unzip -q -o "$PLAYWRIGHT_JAR" "driver/*" -d /app/.playwright/ || true; \
    fi && \
    # Cleanup
    rm -rf /tmp/playwright-extract && \
    chmod -R 755 /app/.playwright

# Ensure /tmp is writable
RUN mkdir -p /tmp && chmod 1777 /tmp

# Set environment variable to point to extracted driver
ENV PLAYWRIGHT_DRIVER_PATH=/app/.playwright/driver

EXPOSE 8080

# Health info (optional)
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=5 \
  CMD curl -f http://localhost:8080/api/v1/scraper/health || exit 1

ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
