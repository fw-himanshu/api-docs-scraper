#!/bin/bash

echo "=== Fixed: Logback Version Conflict ==="
echo ""
echo "Changes made:"
echo "✅ Removed explicit Logback version"
echo "✅ Added Spring Boot dependency management"
echo "✅ Let Spring Boot manage all dependency versions"
echo ""
echo "Building project..."
echo ""

cd /Users/hisharma/workspace/freshworks/self/api-doc-scrapper

./gradlew clean build -x test

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "Starting Spring Boot server..."
    echo "Server: http://localhost:8080"
    echo ""
    echo "Press Ctrl+C to stop"
    ./gradlew bootRun
else
    echo "❌ Build failed. Check errors above."
    exit 1
fi


