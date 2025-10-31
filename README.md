# API Docs Scraper

A Java-based command-line application that scrapes official API documentation pages, extracts structured information about endpoints, and exports it as JSON for generating OpenAPI specifications.

## 🚀 Features

- **Multi-source scraping**: Scrape single or multiple documentation URLs
- **Smart fetching**: Automatically detects JS-rendered pages and uses Playwright when needed
- **Intelligent parsing**: Uses Jsoup to extract endpoints, parameters, and examples
- **Structured output**: Exports clean JSON suitable for LLM processing
- **Clean architecture**: Modular design with clear separation of concerns
- **Robust error handling**: Gracefully handles failed requests and continues processing
- **Comprehensive logging**: SLF4J + Logback for detailed operation logs
- **CLI interface**: User-friendly command-line interface with picocli

## 📋 Requirements

- Java 17 or higher
- Gradle 8.5+ (wrapper included)

## 🏗️ Project Structure

```
api-docs-scraper/
├── src/main/java/com/example/scraper/
│   ├── cli/              # CLI entrypoint
│   ├── model/            # Domain models (Endpoint, Parameter, Example)
│   ├── fetcher/          # Page fetching (HTTP & Playwright)
│   ├── parser/           # HTML parsing with Jsoup
│   ├── exporter/         # JSON export functionality
│   ├── llm/              # LLM prompt generation (optional)
│   ├── ApiDocsScraper.java    # Main orchestrator
│   └── ScraperException.java  # Custom exception
├── src/main/resources/
│   └── logback.xml       # Logging configuration
├── src/test/             # Unit tests
├── build.gradle          # Gradle build configuration
└── README.md
```

## 🔧 Building

Build the project and create an executable JAR:

```bash
./gradlew clean build
```

Create a fat JAR with all dependencies:

```bash
./gradlew shadowJar
```

The executable JAR will be created at: `build/libs/api-docs-scraper-1.0.0.jar`

## 📖 Usage

### Basic Usage

Scrape a single URL:

```bash
java -jar build/libs/api-docs-scraper-1.0.0.jar \
  --url https://developer.calendly.com/api-docs/4b402d5ab3edd-calendly-developer
```

Or using positional arguments:

```bash
java -jar build/libs/api-docs-scraper-1.0.0.jar \
  https://developer.calendly.com/api-docs/4b402d5ab3edd-calendly-developer
```

### Multiple URLs

Scrape multiple documentation pages:

```bash
java -jar build/libs/api-docs-scraper-1.0.0.jar \
  --url https://api1.example.com/docs \
  --url https://api2.example.com/docs \
  --output combined-endpoints.json
```

### With Playwright Rendering

Force Playwright rendering for JavaScript-heavy pages:

```bash
java -jar build/libs/api-docs-scraper-1.0.0.jar \
  --url https://developer.calendly.com/api-docs \
  --render \
  --output calendly-endpoints.json
```

### Verbose Logging

Enable debug logging:

```bash
java -jar build/libs/api-docs-scraper-1.0.0.jar \
  --url https://api.example.com/docs \
  --verbose
```

### Command-Line Options

```
Usage: api-docs-scraper [OPTIONS] [URL...]

Options:
  -u, --url <URL>          URL(s) to scrape (can specify multiple times)
  -o, --output <FILE>      Output file path (default: scraped-endpoints.json)
  -r, --render             Force Playwright rendering for JS-heavy pages
  -v, --verbose            Enable verbose (debug) logging
  -c, --config <FILE>      Path to configuration file (YAML/JSON)
  -h, --help               Show this help message
  -V, --version            Print version information
```

## 📄 Output Format

The scraper exports endpoints in a structured JSON format:

```json
{
  "source": "https://developer.calendly.com/api-docs",
  "scrapedAt": "2025-10-26T12:00:00",
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
          "description": "The user's unique identifier",
          "location": "path"
        }
      ],
      "requestExample": null,
      "responseExample": {
        "language": "json",
        "code": "{\n  \"resource\": {\n    \"uri\": \"https://api.calendly.com/users/AAAA\",\n    \"name\": \"John Doe\",\n    \"email\": \"john@example.com\"\n  }\n}"
      },
      "tags": []
    }
  ]
}
```

## 🧪 Testing

Run the test suite:

```bash
./gradlew test
```

Run tests with detailed output:

```bash
./gradlew test --info
```

## 🎯 Use Cases

### 1. Generate OpenAPI Specifications

Scrape documentation and use the JSON output with an LLM to generate OpenAPI specs:

```bash
# Scrape the documentation
java -jar api-docs-scraper.jar --url https://api.example.com/docs -o endpoints.json

# Use the JSON with an LLM prompt (using the LLMPromptBuilder class)
# The structured data helps the LLM understand your API structure
```

### 2. API Migration

Extract endpoint information from old documentation to plan API migrations or versioning.

### 3. Documentation Analysis

Analyze and compare multiple API documentation sources to ensure consistency.

### 4. Testing Data

Generate test data for API testing frameworks based on documented endpoints.

## 🔌 Architecture

### Fetcher Layer
- **HttpClientFetcher**: Fast HTTP client for static HTML pages
- **PlaywrightFetcher**: Headless browser for JavaScript-rendered pages
- **FetcherFactory**: Smart factory that chooses the appropriate fetcher

### Parser Layer
- **JsoupParser**: Parses HTML and extracts endpoint information using:
  - Header pattern matching (e.g., "GET /users")
  - Code block analysis
  - Parameter table extraction
  - Example code detection

### Exporter Layer
- **JsonExporter**: Converts scraped data to pretty-printed JSON using Gson

### LLM Integration (Optional)
- **LLMPromptBuilder**: Generates formatted prompts for LLMs to create OpenAPI specs

## 📦 Dependencies

- **Jsoup 1.17.2**: HTML parsing
- **Playwright 1.41.0**: JavaScript rendering
- **Gson 2.10.1**: JSON serialization
- **Picocli 4.7.5**: CLI framework
- **SLF4J + Logback**: Logging
- **JUnit 5**: Testing

## 🛠️ Development

### Running from source

```bash
./gradlew run --args="--url https://api.example.com/docs"
```

### Building without tests

```bash
./gradlew build -x test
```

### Running specific tests

```bash
./gradlew test --tests JsoupParserTest
```

## 🐛 Troubleshooting

### Playwright Installation

If Playwright fails to run, install browsers manually:

```bash
./gradlew installPlaywright
```

Or download manually:
```bash
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"
```

### Memory Issues

For large documentation sites, increase heap size:

```bash
java -Xmx2g -jar api-docs-scraper.jar --url https://large-docs.example.com
```

### SSL Issues

If you encounter SSL certificate errors:

```bash
java -Djavax.net.ssl.trustStore=/path/to/truststore \
     -jar api-docs-scraper.jar --url https://api.example.com
```

## 🤝 Contributing

Contributions are welcome! Areas for improvement:

- Additional parser strategies for different documentation formats
- Configuration file support for custom CSS selectors
- Rate limiting between requests
- Proxy support
- Authentication handling for protected documentation

## 📝 License

This project is open source and available under the MIT License.

## 🙏 Acknowledgments

- Built with [Jsoup](https://jsoup.org/) for HTML parsing
- Uses [Playwright](https://playwright.dev/java/) for JavaScript rendering
- CLI powered by [Picocli](https://picocli.info/)

---

**Happy Scraping! 🎉**

# API Docs Scraper

## 🐳 Docker

Build the image (multi-stage, includes Playwright support):

```bash
# From repo root
docker build -t api-docs-scraper:latest .
```

Run the container:

```bash
docker run --rm -p 8080:8080 \
  -e LLM_API_TOKEN="<your-token>" \
  api-docs-scraper:latest
```

Health check:

```bash
curl http://localhost:8080/api/v1/scraper/health
```

Scrape via REST API:

```bash
curl -X POST http://localhost:8080/api/v1/scraper/scrape \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://developer.example.com/api-docs",
    "usePlaywright": true
  }'
```

Notes:
- The runtime image is Playwright-enabled for JS-rendered documentation.
- Set `JAVA_OPTS` (e.g., `-Xmx2g`) if scraping large docs:

```bash
docker run --rm -p 8080:8080 \
  -e JAVA_OPTS="-Xmx2g" \
  -e LLM_API_TOKEN="<your-token>" \
  api-docs-scraper:latest
```

---

