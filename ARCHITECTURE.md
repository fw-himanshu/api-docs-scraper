# Architecture Overview

## System Design

The API Docs Scraper is now a **dual-mode application** supporting both CLI and REST API:

```
┌──────────────────────────────────────────────────────────────────┐
│                         Application Entry                         │
│                        (Application.java)                         │
│  - Spring Boot Application                                        │
│  - Auto-detection: CLI mode vs Web mode                           │
└────┬──────────────────────────────────────────────────────┬───────┘
     │                                                      │
     ▼                                                      ▼
┌──────────────────┐                          ┌─────────────────────┐
│   CLI Mode       │                          │   REST API Mode     │
│  (ScraperCLI)    │                          │  (Spring Boot)      │
│                  │                          │                     │
│  - Picocli args  │                          │  - POST /scrape    │
│  - File output   │                          │  - JSON response   │
│  - Single URL    │                          │  - Async support   │
└────────┬─────────┘                          └──────────┬──────────┘
         │                                             │
         └──────────────────┬──────────────────────────┘
                            │
                            ▼
                ┌───────────────────────────────┐
                │   Orchestration Layer         │
                │   (ApiDocsScraper)            │
                │                               │
                │  - URL fetching               │
                │  - Parser selection           │
                │  - LLM integration            │
                │  - Error handling             │
                │  - Multi-URL support          │
                └─────┬─────────────────────────┘
                      │
          ┌───────────┼───────────┬────────────────┐
          │           │           │                │
          ▼           ▼           ▼                ▼
    ┌────────┐  ┌────────┐  ┌─────────┐   ┌──────────┐
    │Fetcher │  │ Parser │  │ LLM     │   │ Exporter │
    │ Layer  │  │ Layer  │  │ Layer   │   │ Layer    │
    └────────┘  └────────┘  └─────────┘   └──────────┘
```

## Layer Details

### 1. Model Layer (`com.freshworks.scraper.model`)

**Purpose**: Define domain objects for representing API documentation structure.

**Components**:
- `Endpoint`: Represents an API endpoint with method, path, parameters, and examples
- `Parameter`: Represents a parameter (query, path, header, body)
- `Example`: Represents code examples (request/response)
- `ScrapedResult`: Container for all scraped endpoints from a source

**Design Decisions**:
- POJOs with getters/setters for easy JSON serialization
- Mutable objects for builder-pattern construction during parsing
- Rich domain model with helper methods (addParameter, addTag)

### 2. Fetcher Layer (`com.freshworks.scraper.fetcher`)

**Purpose**: Retrieve HTML content from documentation URLs.

**Components**:
- `PageFetcher` (interface): Abstract fetching contract
- `HttpClientFetcher`: Fast HTTP-based fetching for static pages
- `PlaywrightFetcher`: Browser-based fetching for JS-rendered pages
- `FetcherFactory`: Smart factory for selecting appropriate fetcher

**Design Decisions**:
- Strategy pattern for swappable fetching implementations
- Factory pattern for intelligent fetcher selection
- Resource management (closeable interface) for Playwright cleanup
- Auto-detection of JS-heavy sites by URL patterns

**Flow**:
```
URL → FetcherFactory → Fetcher Selection
                          │
         ┌────────────────┴──────────────┐
         ▼                               ▼
    HttpClientFetcher            PlaywrightFetcher
    (static HTML)                (JS-rendered)
         │                               │
         └────────────┬──────────────────┘
                      ▼
                  HTML Content
```

### 3. Parser Layer (`com.freshworks.scraper.parser`)

**Purpose**: Extract structured endpoint information from HTML using multiple parsing strategies including LLM-enhanced extraction.

**Components**:
- `DocumentParser` (interface): Parsing contract
- `JsoupParser`: Jsoup-based implementation with DOM parsing strategies
- `StoplightParser`: Specialized parser for Stoplight-based documentation
- `LLMSmartParser`: Generic LLM-powered parser for any complex documentation
- `LLMEnhancedParser`: Intelligent parser that uses LLM to discover and extract endpoints

**Parser Selection Strategy**:
```
URL Analysis → Parser Selection
    │
    ├─→ LLM available → LLMSmartParser (works with any documentation)
    │
    ├─→ Stoplight detected (no LLM) → StoplightParser
    │
    └─→ Default → JsoupParser
```

**LLMSmartParser Flow** (LLM-Enhanced):
```
1. Fetch base documentation page
   │
2. Ask LLM: "Discover all API endpoints on this page"
   │
3. LLM returns list of endpoint metadata
   │
4. For each endpoint:
   │   ├─→ Fetch dedicated documentation page (if available)
   │   ├─→ Ask LLM: "Extract complete details for this endpoint"
   │   └─→ Parse LLM response into Endpoint object
   │
5. Return all discovered endpoints
```

**Parsing Strategies** (JsoupParser):
1. **Header-based parsing**: Looks for headings containing HTTP methods
2. **Code block parsing**: Extracts endpoints from code examples
3. **Table parsing**: Finds parameter tables and extracts metadata

**Design Decisions**:
- LLM integration for complex documentation sites
- Multiple complementary strategies to maximize coverage
- Automatic parser selection based on URL and content
- Retry logic for transient LLM or fetch failures
- Graceful fallback to basic parsing if LLM fails

### 4. Exporter Layer (`com.freshworks.scraper.exporter`)

**Purpose**: Export scraped data to structured formats.

**Components**:
- `JsonExporter`: Gson-based JSON serialization

**Design Decisions**:
- Pretty-printed JSON for human readability
- Include null values for complete schema visibility
- File-based and string-based export options
- Extensible design (future: YAML, CSV exporters)

### 5. LLM Integration Layer (`com.freshworks.scraper.llm`)

**Purpose**: Use Large Language Models for intelligent endpoint discovery and extraction.

**Components**:
- `LLMClient`: HTTP client for Freshworks LLM API (Claude 4 Sonnet)
- `LLMException`: Custom exception for LLM-related errors

**Features**:
- **Endpoint Discovery**: LLM identifies all API endpoints from documentation pages
- **Detail Extraction**: LLM extracts complete endpoint details (parameters, examples, descriptions)
- **Configurable API**: Token-based authentication, configurable URLs and models
- **Retry Mechanism**: Automatic retries (3 attempts) for transient failures
- **Comprehensive Logging**: Detailed logs for all LLM API calls with timing
- **Response Handling**: Robust parsing of various LLM response formats

**LLM API Configuration**:
- URL: `https://cloudverse.freshworkscorp.com/api/v2/chat`
- Model: `anthropic-claude-4-sonnet`
- Temperature: 0 (deterministic responses)
- Timeout: 120 seconds
- Token: Configurable via `application.properties` or environment variable

**LLM Flow**:
```
Parse Request
    │
    ├─→ Detect if LLM needed (Calendly, Stoplight, etc.)
    │
    ├─→ Initialize LLMClient with token
    │
    ├─→ For each LLM call:
    │   │   ├─→ Build system + user prompts
    │   │   ├─→ Send HTTP POST request
    │   │   ├─→ Wait for response (with timeout)
    │   │   ├─→ Parse JSON response
    │   │   ├─→ Handle markdown code blocks
    │   │   └─→ Return structured data
    │
    └─→ Retry on failures (up to 3 attempts)
```

### 6. Web Layer (`com.freshworks.scraper.web`) - Spring Boot

**Purpose**: REST API for programmatic access to scraping functionality.

**Components**:
- `Application`: Spring Boot entry point with dual-mode operation
- `ApiScraperController`: REST controller for scraping endpoints
- `ScrapeRequest`: Request DTO with validation
- `ScrapeResponse`: Response DTO with standardized format

**REST Endpoints**:
- `POST /api/v1/scraper/scrape` - Main scraping endpoint
- `GET /api/v1/scraper/health` - Health check
- `GET /api/v1/scraper/config` - Configuration info

**Request Format**:
```json
{
  "url": "https://developer.example.com/api-docs",
  "usePlaywright": true,
  "verbose": false,
  "llmToken": "optional-token-override"
}
```

**Response Format**:
```json
{
  "success": true,
  "message": "Successfully scraped 5 endpoints",
  "data": {
    "sourceUrl": "...",
    "endpointCount": 5,
    "endpoints": [...]
  }
}
```

### 7. Configuration Layer (`com.freshworks.scraper.config`)

**Purpose**: Manage application configuration and LLM settings.

**Components**:
- `AppConfig`: Spring Boot configuration component with `@Value` injection
- `application.properties`: Configuration file

**Configuration Priority**:
1. Command-line arguments
2. Environment variables
3. `application.properties` file
4. Default values

**Key Settings**:
- `llm.api.token`: LLM API authentication token
- `llm.api.url`: LLM API endpoint URL
- `llm.api.model`: LLM model name
- `server.port`: Spring Boot server port (default: 8080)

### 8. CLI Layer (`com.freshworks.scraper.cli`)

**Purpose**: User-facing command-line interface (legacy, still supported).

**Components**:
- `ScraperCLI`: Picocli-based CLI with argument parsing

**Features**:
- Multiple input methods (--url flag, positional args)
- Sensible defaults (output file, fetcher selection)
- Help and version commands
- Verbose mode for debugging
- Proper exit codes for scripting
- LLM token configuration support

## Data Flow

### Complete Scraping Flow (REST API Mode)

```
1. REST Request
   POST /api/v1/scraper/scrape
   {
     "url": "https://developer.example.com/api-docs/...",
     "usePlaywright": true
   }

2. Controller Layer
   └─→ ApiScraperController.scrape()
       ├─→ Load LLM token from config
       ├─→ Initialize ApiDocsScraper with LLM
       └─→ Call scraper.scrape(url)

3. Orchestration (ApiDocsScraper)
   ├─→ Fetch page: PlaywrightFetcher
   │   ├─→ Check browser availability
   │   ├─→ Create new page
   │   ├─→ Navigate with timeout (60s)
   │   ├─→ Wait for load state
   │   ├─→ Wait for network idle
   │   ├─→ Scroll to trigger lazy loading
   │   └─→ Return HTML content
   │
   └─→ Parser Selection
       ├─→ Analyze URL and HTML
       └─→ Select LLMSmartParser (if LLM available)

4. LLM-Enhanced Parsing (LLMSmartParser)
   Step 1: Discovery
   ├─→ Extract text from HTML (max 10k chars)
   ├─→ Call LLM: "Discover all API endpoints"
   ├─→ LLM returns JSON array of endpoints
   └─→ Parse endpoint list
   
   Step 2: Detailed Extraction
   For each endpoint:
   ├─→ Fetch dedicated page (if URL available)
   ├─→ Call LLM: "Extract complete endpoint details"
   ├─→ Parse LLM response
   ├─→ Extract parameters, examples, descriptions
   └─→ Create Endpoint object
   
   └─→ Return List<Endpoint>

5. Result Building
   └─→ ScrapedResult
       ├─→ Add endpoints
       ├─→ Add metadata (source, timestamp, count)
       └─→ Calculate stats

6. Response
   └─→ ApiScraperController
       ├─→ Wrap in ScrapeResponse
       ├─→ Add success status
       └─→ Return JSON to client
```

### CLI Mode Flow

```
1. CLI Execution
   └─→ Application.main() detects CLI mode
       └─→ Call ScraperCLI.main(args)

2. CLI Parsing
   └─→ Picocli parses arguments
       ├─→ --url, --output, --render, --llm-token
       └─→ Build scraping request

3. Scraping (Same as REST mode)
   └─→ ApiDocsScraper.scrape()
       └─→ Fetch → Parse → Build Result

4. Export
   └─→ JsonExporter.export()
       ├─→ Serialize to JSON
       ├─→ Write to file
       └─→ Print success message

5. Exit
   └─→ Proper exit code (0 = success, 1 = error)
```

### LLM Integration Flow

```
LLMSmartParser (Generic, works with any documentation)
    │
    ├─→ Initial Discovery Request
    │   ├─→ Build system prompt: "You are an API documentation expert..."
    │   ├─→ Build user prompt: "Analyze this API documentation page and list ALL endpoints..."
    │   ├─→ LLMClient.call()
    │   ├─→ HTTP POST to Freshworks API
    │   ├─→ Wait for response (with 120s timeout)
    │   ├─→ Parse JSON response
    │   └─→ Extract endpoint list
    │
    └─→ Detailed Extraction (for each endpoint)
        ├─→ Build system prompt: "Extract complete endpoint details..."
        ├─→ Build user prompt: "Extract details for GET /users..."
        ├─→ LLMClient.call()
        ├─→ Retry on failure (up to 3 attempts)
        ├─→ Parse structured response
        └─→ Build Endpoint object with parameters
```

## Error Handling Strategy

### Retry Mechanisms

**Playwright Fetcher**:
- ✅ 3 automatic retries on browser close errors
- ✅ 2-second delay between retries
- ✅ 60-second navigation timeout
- ✅ Browser availability checks

**LLM Client**:
- ✅ 3 automatic retries on HTTP errors
- ✅ 120-second request timeout
- ✅ Graceful fallback if LLM unavailable
- ✅ Detailed logging for debugging

### Graceful Degradation

The scraper continues processing even when individual endpoints fail:

```
Scrape Operation
    │
    ├─→ Endpoint 1: ✅ Success
    │
    ├─→ Endpoint 2: ❌ LLM error → Retry → ✅ Success
    │
    ├─→ Endpoint 3: ❌ Fetch error → Retry → ✅ Success
    │
    └─→ Endpoint 4: ❌ All retries failed → Skip and continue

Result: 3 of 4 endpoints successfully extracted
```

### Exception Hierarchy

```
Exception
  └─→ ScraperException
       ├─→ FetchException (thrown by fetchers)
       │    ├─→ PlaywrightFetcher: retry on browser errors
       │    └─→ HttpClientFetcher: network errors
       └─→ LLMException (thrown by LLM calls)
            └─→ Network errors, timeout, invalid response
```

### Logging Strategy

**Comprehensive Logging Throughout**:
- 🚀 Operation start/end with timing
- 📍 URL being processed
- 🔍 Parser selection
- 🤖 LLM API calls with timing
- 📊 Progress (e.g., "Processing endpoint [1/15]")
- ✅ Success confirmations
- ⚠️ Warnings for retries
- ❌ Errors with context

**Log Levels**:
- `INFO`: Main operations, progress, results
- `DEBUG`: Detailed step-by-step information
- `WARN`: Recoverable issues (fallbacks)
- `ERROR`: Failures that affect the operation

## Testing Strategy

### Test Coverage

1. **Unit Tests**
   - `JsoupParserTest`: Tests all parsing strategies with sample HTML
   - `JsonExporterTest`: Tests JSON serialization and file export
   - `LLMPromptBuilderTest`: Tests prompt generation

2. **Test Resources**
   - `sample-api-doc.html`: Realistic API documentation sample
   - Contains multiple endpoints with parameters and examples

3. **Integration Testing** (Future)
   - End-to-end scraping of real documentation sites
   - Comparison with golden files

## Extension Points

### Adding New Fetchers

Implement the `PageFetcher` interface:

```java
public class CustomFetcher implements PageFetcher {
    @Override
    public String fetch(String url) throws FetchException {
        // Custom fetching logic
    }
    
    @Override
    public void close() {
        // Cleanup
    }
}
```

### Adding New Parsers

Implement the `DocumentParser` interface:

```java
public class CustomParser implements DocumentParser {
    @Override
    public List<Endpoint> parse(String html, String sourceUrl) {
        // Custom parsing logic
    }
}
```

### Adding New Exporters

Follow the pattern of `JsonExporter`:

```java
public class YamlExporter {
    public void export(ScrapedResult result, Path outputPath) {
        // YAML export logic
    }
}
```

## Performance Considerations

### Playwright Overhead

Playwright fetching is significantly slower than HTTP fetching:
- HTTP: ~100-500ms per page
- Playwright: ~3-10 seconds per page (includes browser startup, JS execution)

**Mitigation**:
- Only use Playwright when necessary (--render flag or auto-detection)
- Reuse browser instance across multiple pages (implemented)
- Configurable wait times

### Memory Usage

For large documentation sites:
- Each page's HTML is loaded into memory
- Parsed endpoint objects are retained until export
- Playwright browser process adds ~100-200MB

**Mitigation**:
- Process and export URLs in batches (future enhancement)
- Increase JVM heap size for large jobs: `java -Xmx2g -jar ...`

## Security Considerations

1. **SSL/TLS**: Trusts system certificate store
2. **Redirects**: Follows redirects automatically (HTTP client)
3. **User-Agent**: Identifies as "API-Docs-Scraper/1.0"
4. **Rate Limiting**: Not implemented (future enhancement for respectful scraping)

## Current Implementation Status

### ✅ Completed Features

1. **Spring Boot REST API**: Full REST API with health checks
2. **LLM Integration**: Claude 4 Sonnet via Freshworks API
3. **Enhanced Logging**: Comprehensive logs with timing and progress
4. **Retry Mechanisms**: Automatic retries for transient failures
5. **Parser Selection**: Intelligent parser selection based on URL and content
6. **Calendly Support**: Specialized parser for Calendly's complex documentation
7. **Playwright Support**: JavaScript rendering with retry logic
8. **Dual Mode**: CLI and REST API in one application

### 🔄 In Progress

1. **Async Processing**: Background job processing for long-running scrapes
2. **Result Caching**: Cache results to avoid re-scraping unchanged docs

### 📋 Future Enhancements

1. **Rate Limiting**: Configurable delays between requests
2. **Authentication**: Support for API key or OAuth-protected docs
3. **Caching**: Cache fetched pages to avoid re-downloading
4. **Incremental Updates**: Only scrape changed endpoints
5. **Output Formats**: YAML, CSV, Markdown exporters
6. **OpenAPI Direct Generation**: Generate OpenAPI spec directly
7. **Webhook Support**: Notify on completion of long-running jobs
8. **Multi-language Support**: Extract documentation in multiple languages

