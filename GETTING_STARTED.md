# Getting Started with API Docs Scraper

This guide will help you get up and running with the API Docs Scraper in under 5 minutes.

## Prerequisites

- Java 17 or higher
- Internet connection (for downloading dependencies)

## Quick Start (3 Steps)

### Step 1: Navigate to the Project

```bash
cd /tmp/api-docs-scraper
```

### Step 2: Build the Project

```bash
./gradlew clean build
```

This will:
- Download all dependencies
- Compile the Java code
- Run all tests
- Create a JAR file

### Step 3: Run Your First Scrape

```bash
# Using the generated JAR
java -jar build/libs/api-docs-scraper-1.0.0.jar --help
```

## Example Usage

### Scrape a Documentation Page

```bash
java -jar build/libs/api-docs-scraper-1.0.0.jar \
  --url "https://developer.calendly.com/api-docs/4b402d5ab3edd-calendly-developer" \
  --output calendly-api.json
```

### With JavaScript Rendering (Playwright)

```bash
java -jar build/libs/api-docs-scraper-1.0.0.jar \
  --url "https://developer.calendly.com/api-docs/4b402d5ab3edd-calendly-developer" \
  --render \
  --output calendly-api.json
```

### Scrape Multiple URLs

```bash
java -jar build/libs/api-docs-scraper-1.0.0.jar \
  https://api1.example.com/docs \
  https://api2.example.com/docs \
  --output combined-api.json
```

### With Verbose Logging

```bash
java -jar build/libs/api-docs-scraper-1.0.0.jar \
  --url "https://api.example.com/docs" \
  --verbose
```

## Output Example

After running the scraper, you'll get a JSON file like this:

```json
{
  "source": "https://developer.calendly.com/api-docs",
  "scrapedAt": "2025-10-26T12:34:56",
  "endpoints": [
    {
      "method": "GET",
      "path": "/users/{uuid}",
      "summary": "Get User",
      "description": "Returns information about a specified User.",
      "parameters": [
        {
          "name": "uuid",
          "type": "string",
          "required": true,
          "description": "The user's unique identifier"
        }
      ],
      "responseExample": {
        "language": "json",
        "code": "{\n  \"resource\": {...}\n}"
      }
    }
  ]
}
```

## Using the Output with an LLM

You can use the LLMPromptBuilder class to generate a prompt for creating OpenAPI specs:

```java
import com.freshworks.scraper.llm.LLMPromptBuilder;
import com.freshworks.scraper.model.ScrapedResult;

// Load your scraped result
ScrapedResult result = ... // load from JSON

// Generate prompt
LLMPromptBuilder builder = new LLMPromptBuilder();
String prompt = builder.buildPrompt(result);

// Feed this prompt to an LLM like ChatGPT, Claude, etc.
System.out.println(prompt);
```

## Project Structure Overview

```
api-docs-scraper/
├── src/main/java/          # Source code
│   └── com/example/scraper/
│       ├── cli/            # CLI interface
│       ├── model/          # Domain models
│       ├── fetcher/        # Page fetching
│       ├── parser/         # HTML parsing
│       ├── exporter/       # JSON export
│       └── llm/            # LLM integration
├── src/test/               # Tests
├── build.gradle            # Build configuration
└── README.md              # Full documentation
```

## Common Commands

### Run Tests
```bash
./gradlew test
```

### Create Fat JAR (with all dependencies)
```bash
./gradlew shadowJar
# Output: build/libs/api-docs-scraper-1.0.0.jar
```

### Run from Source
```bash
./gradlew run --args="--url https://api.example.com/docs"
```

### Clean Build
```bash
./gradlew clean build
```

## Troubleshooting

### Playwright Issues

If Playwright fails, you may need to install browsers:

```bash
# Install Playwright browsers
./gradlew installPlaywright
```

Or manually:
```bash
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"
```

### Memory Issues

For large documentation sites:

```bash
java -Xmx2g -jar api-docs-scraper.jar --url https://large-docs.example.com
```

### Connection Issues

Check your internet connection and firewall settings. The scraper needs to:
- Download Gradle dependencies (first build only)
- Fetch documentation pages
- Download Playwright browsers (if using --render)

## What's Next?

1. **Read the full README**: Check out `README.md` for comprehensive documentation
2. **Explore the architecture**: See `ARCHITECTURE.md` for design details
3. **Run the tests**: `./gradlew test` to see how everything works
4. **Try different sites**: Experiment with various API documentation sites
5. **Customize**: Modify `config.example.yaml` for site-specific scraping

## Getting Help

- **Documentation**: See `README.md` for detailed usage
- **Architecture**: See `ARCHITECTURE.md` for technical details
- **Examples**: Check test files in `src/test/java/`
- **Logs**: Check `api-docs-scraper.log` for debugging

## Command Reference

| Option | Description | Example |
|--------|-------------|---------|
| `-u, --url` | URL to scrape | `--url https://api.example.com` |
| `-o, --output` | Output file path | `--output results.json` |
| `-r, --render` | Use Playwright rendering | `--render` |
| `-v, --verbose` | Enable debug logging | `--verbose` |
| `-h, --help` | Show help message | `--help` |
| `-V, --version` | Show version | `--version` |

---

**Ready to scrape! 🚀**

For more details, check out the full [README.md](README.md).

