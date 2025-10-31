#!/bin/bash

# Quick Start Script for API Docs Scraper
# This script builds the project and runs a test scrape

set -e

echo "🚀 API Docs Scraper - Quick Start"
echo "=================================="
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed or not in PATH"
    echo "Please install Java 17 or higher"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "✓ Java version: $JAVA_VERSION"
echo ""

# Build the project
echo "📦 Building project..."
./gradlew clean build

echo ""
echo "✓ Build completed successfully!"
echo ""

# Check if jar was created
JAR_FILE="build/libs/api-docs-scraper-1.0.0.jar"
if [ -f "$JAR_FILE" ]; then
    echo "✓ JAR file created: $JAR_FILE"
else
    echo "❌ Error: JAR file not found"
    exit 1
fi

echo ""
echo "=================================="
echo "🎉 Setup Complete!"
echo ""
echo "Usage examples:"
echo ""
echo "1. Scrape a single URL:"
echo "   java -jar $JAR_FILE --url https://api.example.com/docs"
echo ""
echo "2. Scrape with Playwright rendering:"
echo "   java -jar $JAR_FILE --url https://api.example.com/docs --render"
echo ""
echo "3. Scrape multiple URLs:"
echo "   java -jar $JAR_FILE \\"
echo "     --url https://api1.example.com/docs \\"
echo "     --url https://api2.example.com/docs \\"
echo "     --output combined.json"
echo ""
echo "4. Show help:"
echo "   java -jar $JAR_FILE --help"
echo ""

