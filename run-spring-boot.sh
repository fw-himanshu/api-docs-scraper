#!/bin/bash
# Run Spring Boot Application

echo "=== Building and Running Spring Boot API ==="
echo ""

cd /Users/hisharma/workspace/freshworks/self/api-doc-scrapper

echo "Step 1: Building project..."
./gradlew clean build -x test

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "Step 2: Starting Spring Boot server..."
    echo "Server will start on http://localhost:8080"
    echo ""
    echo "Test endpoints:"
    echo "  Health: curl http://localhost:8080/api/v1/scraper/health"
    echo "  Scrape: curl -X POST http://localhost:8080/api/v1/scraper/scrape -H 'Content-Type: application/json' -d '{\"url\":\"https://developer.calendly.com/api-docs/d7755e2f9e5fe-calendly-api\",\"usePlaywright\":true}'"
    echo ""
    echo "Starting server now..."
    ./gradlew bootRun
else
    echo "❌ Build failed. Check errors above."
    exit 1
fi


