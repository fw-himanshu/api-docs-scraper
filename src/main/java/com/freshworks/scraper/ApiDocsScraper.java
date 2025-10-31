package com.freshworks.scraper;

import com.freshworks.scraper.exporter.JsonExporter;
import com.freshworks.scraper.fetcher.FetchException;
import com.freshworks.scraper.fetcher.FetcherFactory;
import com.freshworks.scraper.fetcher.PageFetcher;
import com.freshworks.scraper.llm.LLMClient;
import com.freshworks.scraper.llm.LLMException;
import com.freshworks.scraper.llm.OpenAPIGenerator;
import com.freshworks.scraper.model.Endpoint;
import com.freshworks.scraper.model.ScrapedResult;
import com.freshworks.scraper.parser.LLMSmartParser;
import com.freshworks.scraper.parser.DocumentParser;
import com.freshworks.scraper.parser.JsoupParser;
import com.freshworks.scraper.parser.ReadmeParser;
import com.freshworks.scraper.parser.StoplightParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Main orchestrator for scraping API documentation.
 */
public class ApiDocsScraper {
    private static final Logger logger = LoggerFactory.getLogger(ApiDocsScraper.class);
    
    private final boolean usePlaywright;
    private final LLMClient llmClient;
    private final JsonExporter exporter;

    public ApiDocsScraper(boolean usePlaywright) {
        this(usePlaywright, null);
    }

    public ApiDocsScraper(boolean usePlaywright, String llmToken) {
        this.usePlaywright = usePlaywright;
        this.llmClient = (llmToken != null && !llmToken.isEmpty()) ? new LLMClient(llmToken) : null;
        this.exporter = new JsonExporter();
    }

    /**
     * Scrapes a single URL and returns the result.
     * 
     * @param url The URL to scrape
     * @return ScrapedResult containing all extracted endpoints
     * @throws ScraperException if scraping fails
     */
    public ScrapedResult scrape(String url) throws ScraperException {
        long startTime = System.currentTimeMillis();
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        logger.info("🚀 Starting Scrape Operation");
        logger.info("   📍 URL: {}", url);
        logger.info("   🎭 Playwright: {}", usePlaywright ? "Enabled" : "Disabled");
        logger.info("   🤖 LLM: {}", llmClient != null ? "Available" : "Not Available");
        
        PageFetcher fetcher = FetcherFactory.createFetcher(url, usePlaywright);
        
        try {
            logger.info("   📥 Fetching page content...");
            long fetchStart = System.currentTimeMillis();
            String html = fetcher.fetch(url);
            long fetchDuration = System.currentTimeMillis() - fetchStart;
            logger.info("   ✅ Page fetched in {} ms ({} chars)", fetchDuration, html.length());
            
            // Choose the appropriate parser based on the URL and content
            DocumentParser parser = selectParser(url, html);
            logger.info("   🔍 Parser selected: {}", parser.getClass().getSimpleName());
            
            // Parse the HTML
            logger.info("   📊 Parsing HTML content...");
            long parseStart = System.currentTimeMillis();
            List<Endpoint> endpoints = parser.parse(html, url);
            long parseDuration = System.currentTimeMillis() - parseStart;
            
            // Build result
            ScrapedResult result = new ScrapedResult(url);
            result.setEndpoints(endpoints);
            
            long totalDuration = System.currentTimeMillis() - startTime;
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.info("✅ Scrape Complete!");
            logger.info("   📊 Endpoints extracted: {}", endpoints.size());
            logger.info("   ⏱️  Total time: {} ms", totalDuration);
            logger.info("   🕐 Fetch time: {} ms ({}%)", fetchDuration, (fetchDuration * 100 / totalDuration));
            logger.info("   🔍 Parse time: {} ms ({}%)", parseDuration, (parseDuration * 100 / totalDuration));
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            return result;
            
        } catch (FetchException e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            logger.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.error("❌ Scrape Failed after {} ms", totalDuration);
            logger.error("   📍 URL: {}", url);
            logger.error("   💥 Error: {}", e.getMessage());
            logger.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            throw new ScraperException("Failed to scrape URL: " + url, e);
        } finally {
            fetcher.close();
        }
    }

    /**
     * Selects the appropriate parser based on URL and HTML content.
     */
    private DocumentParser selectParser(String url, String html) {
        // Check if it's a README.md file
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("readme") || lowerUrl.contains("/readme.md") || lowerUrl.endsWith("readme")) {
            logger.info("Detected README.md file");
            if (llmClient != null) {
                logger.info("Using ReadmeParser with LLM");
                return new ReadmeParser(llmClient);
            } else {
                logger.warn("LLM not available for README parsing, falling back to JsoupParser");
                return new JsoupParser();
            }
        }
        
        // If LLM client is available, use LLM-powered parsing for any complex documentation
        if (llmClient != null) {
            logger.info("LLM available - using LLM Smart Parser for intelligent extraction");
            return new LLMSmartParser(llmClient, usePlaywright);
        }
        
        // Check if it's a Stoplight-based documentation
        if (url.contains("stoplight.io") ||
            html.contains("sl-elements") ||
            html.contains("stoplight") ||
            html.contains("HttpOperation")) {
            
            logger.info("Detected Stoplight-based documentation");
            logger.info("Using StoplightParser");
            return new StoplightParser();
        }
        
        // Default to standard Jsoup parser
        logger.info("Using standard JsoupParser");
        return new JsoupParser();
    }
    
    /**
     * Determines if the URL/HTML represents a documentation index page
     * (as opposed to a single endpoint page).
     */
    private boolean isDocumentationIndexPage(String url, String html) {
        // Look for patterns that indicate this is an index/navigation page
        String lowerUrl = url.toLowerCase();
        String lowerHtml = html.toLowerCase();
        
        // Check URL patterns
        if (lowerUrl.contains("/api-docs") || 
            lowerUrl.contains("/api-reference") ||
            lowerUrl.contains("/docs/api") ||
            lowerUrl.endsWith("/api") ||
            lowerUrl.endsWith("/docs")) {
            
            // Check HTML content for navigation/index indicators
            if (lowerHtml.contains("nav") || 
                lowerHtml.contains("sidebar") ||
                lowerHtml.contains("table of contents") ||
                lowerHtml.contains("api reference")) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Scrapes multiple URLs and combines results.
     * 
     * @param urls List of URLs to scrape
     * @return Combined ScrapedResult
     */
    public ScrapedResult scrapeMultiple(List<String> urls) {
        logger.info("Scraping {} URLs", urls.size());
        
        ScrapedResult combinedResult = new ScrapedResult("multiple sources");
        
        for (String url : urls) {
            try {
                ScrapedResult result = scrape(url);
                combinedResult.getEndpoints().addAll(result.getEndpoints());
            } catch (ScraperException e) {
                logger.error("Failed to scrape URL: {} - continuing with remaining URLs", url, e);
            }
        }
        
        logger.info("Scraping complete. Total endpoints: {}", combinedResult.getEndpoints().size());
        return combinedResult;
    }

    /**
     * Scrapes a URL and exports the result to a file.
     * 
     * @param url The URL to scrape
     * @param outputPath The output file path
     * @throws ScraperException if scraping or export fails
     */
    public void scrapeAndExport(String url, Path outputPath) throws ScraperException {
        ScrapedResult result = scrape(url);
        
        try {
            exporter.export(result, outputPath);
        } catch (IOException e) {
            throw new ScraperException("Failed to export results", e);
        }
    }

    /**
     * Scrapes multiple URLs and exports the combined result.
     * 
     * @param urls List of URLs to scrape
     * @param outputPath The output file path
     * @throws ScraperException if export fails
     */
    public void scrapeMultipleAndExport(List<String> urls, Path outputPath) throws ScraperException {
        ScrapedResult result = scrapeMultiple(urls);
        
        try {
            exporter.export(result, outputPath);
        } catch (IOException e) {
            throw new ScraperException("Failed to export results", e);
        }
    }
    
    /**
     * Generates OpenAPI 3.0 specification from a ScrapedResult using LLM.
     * 
     * @param result The scraped result containing endpoints
     * @param baseUrl The base URL for the API (optional, will be derived from source if not provided)
     * @return OpenAPI 3.0 specification as JSON string
     * @throws ScraperException if generation fails
     */
    public String generateOpenAPI(ScrapedResult result, String baseUrl) throws ScraperException {
        if (llmClient == null) {
            throw new ScraperException("LLM client is required to generate OpenAPI specification");
        }
        
        try {
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.info("📝 Generating OpenAPI 3.0 Specification");
            logger.info("   📊 Endpoints: {}", result.getEndpoints().size());
            
            OpenAPIGenerator openAPIGenerator = new OpenAPIGenerator(llmClient);
            String openApiSpec = openAPIGenerator.generateOpenAPI(result, baseUrl);
            
            logger.info("✅ OpenAPI specification generated successfully");
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            return openApiSpec;
            
        } catch (LLMException e) {
            logger.error("Failed to generate OpenAPI specification", e);
            throw new ScraperException("Failed to generate OpenAPI specification", e);
        }
    }
    
    /**
     * Scrapes a URL and generates OpenAPI 3.0 specification.
     * 
     * @param url The URL to scrape
     * @param baseUrl The base URL for the API (optional)
     * @return OpenAPI 3.0 specification as JSON string
     * @throws ScraperException if scraping or generation fails
     */
    public String scrapeAndGenerateOpenAPI(String url, String baseUrl) throws ScraperException {
        ScrapedResult result = scrape(url);
        return generateOpenAPI(result, baseUrl);
    }
}

