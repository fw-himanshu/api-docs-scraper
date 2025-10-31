package com.freshworks.scraper.web;

import com.freshworks.scraper.ApiDocsScraper;
import com.freshworks.scraper.ScraperException;
import com.freshworks.scraper.config.AppConfig;
import com.freshworks.scraper.exporter.JsonExporter;
import com.freshworks.scraper.model.Endpoint;
import com.freshworks.scraper.model.ScrapedResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST API controller for the API documentation scraper.
 */
@RestController
@RequestMapping("/api/v1/scraper")
@Validated
public class ApiScraperController {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiScraperController.class);
    
    private final AppConfig appConfig;
    
    public ApiScraperController(AppConfig appConfig) {
        this.appConfig = appConfig;
    }
    
    /**
     * POST /api/v1/scraper/scrape
     * 
     * Scrapes API documentation from the provided URL(s) and returns extracted endpoints.
     * 
     * @param request The scrape request containing URL and options
     * @return ScrapeResponse with extracted endpoints
     */
    @PostMapping(value = "/scrape", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ScrapeResponse> scrape(@Valid @RequestBody ScrapeRequest request) {
        logger.info("Scrape API request received for URL: {}", request.getUrl());
        
        try {
            // Determine LLM token (from request, environment, or config)
            String llmToken = determineLlmToken(request.getLlmToken());
            
            // Create scraper instance with options
            ApiDocsScraper scraper = new ApiDocsScraper(request.isUsePlaywright(), llmToken);
            
            // Perform the scrape
            ScrapedResult result;
            if (request.getAdditionalUrls() != null && !request.getAdditionalUrls().isEmpty()) {
                // Multiple URLs
                List<String> allUrls = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(request.getUrl()),
                        request.getAdditionalUrls().stream()
                ).toList();
                result = scraper.scrapeMultiple(allUrls);
            } else {
                // Single URL
                result = scraper.scrape(request.getUrl());
            }
            
            // Get results
            List<Endpoint> endpoints = result.getEndpoints();
            
            // Generate OpenAPI 3.0 specification using LLM
            String openApiSpec = null;
            try {
                logger.info("Generating OpenAPI 3.0 specification using LLM...");
                openApiSpec = scraper.generateOpenAPI(result, extractBaseUrl(request.getUrl()));
                logger.info("OpenAPI specification generated successfully");
            } catch (ScraperException e) {
                logger.error("Failed to generate OpenAPI specification, returning raw endpoints", e);
                // Continue with raw endpoint response
            }
            
            // Prepare response
            ScrapeResponse response;
            if (openApiSpec != null) {
                response = ScrapeResponse.successWithOpenAPI(
                        "Successfully scraped " + endpoints.size() + " endpoints and generated OpenAPI 3.0 spec",
                        endpoints,
                        openApiSpec
                );
            } else {
                // Fallback to JSON if OpenAPI generation fails
                String jsonOutput = generateJsonOutput(result);
                response = ScrapeResponse.success(
                        "Successfully scraped " + endpoints.size() + " endpoints (OpenAPI generation failed)",
                        endpoints,
                        jsonOutput
                );
            }
            response.setSourceUrl(request.getUrl());
            
            logger.info("Scrape API request completed successfully. Extracted {} endpoints", 
                    endpoints.size());
            
            return ResponseEntity.ok(response);
            
        } catch (ScraperException e) {
            logger.error("Scraping failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ScrapeResponse.failure("Scraping failed: " + e.getMessage(), 
                            e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error during scraping", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ScrapeResponse.failure("Unexpected error: " + e.getMessage(), 
                            e.getMessage()));
        }
    }
    
    /**
     * GET /api/v1/scraper/health
     * 
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<java.util.Map<String, Object>> health() {
        logger.info("Health check requested");
        
        java.util.Map<String, Object> status = new java.util.HashMap<>();
        status.put("status", "UP");
        status.put("timestamp", LocalDateTime.now());
        status.put("llmConfigured", appConfig.getLLMApiToken() != null);
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * GET /api/v1/scraper/config
     * 
     * Returns current configuration (without sensitive data).
     */
    @GetMapping("/config")
    public ResponseEntity<java.util.Map<String, Object>> config() {
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        config.put("llmConfigured", appConfig.getLLMApiToken() != null);
        config.put("llmApiUrl", appConfig.getLLMApiUrl());
        config.put("llmModel", appConfig.getLLMApiModel());
        config.put("version", "1.0.0");
        
        return ResponseEntity.ok(config);
    }
    
    /**
     * Determines which LLM token to use based on priority:
     * 1. Request parameter
     * 2. Environment variable
     * 3. Config file
     */
    private String determineLlmToken(String requestToken) {
        if (requestToken != null && !requestToken.isEmpty()) {
            return requestToken;
        }
        
        String envToken = System.getenv("LLM_API_TOKEN");
        if (envToken != null && !envToken.isEmpty()) {
            return envToken;
        }
        
        return appConfig.getLLMApiToken();
    }
    
    /**
     * Generates JSON output from the scraped result.
     */
    private String generateJsonOutput(ScrapedResult result) {
        try {
            // Create a temporary file path
            Path tempFile = Files.createTempFile("scraper-output-" + UUID.randomUUID(), ".json");
            tempFile.toFile().deleteOnExit();
            
            // Export to file
            JsonExporter exporter = new JsonExporter();
            exporter.export(result, tempFile);
            
            // Read back the JSON
            String json = Files.readString(tempFile);
            
            // Clean up
            Files.deleteIfExists(tempFile);
            
            return json;
            
        } catch (IOException e) {
            logger.error("Failed to generate JSON output", e);
            return "{\"error\": \"Failed to generate JSON output: " + e.getMessage() + "\"}";
        }
    }
    
    /**
     * Extracts base URL from a full URL.
     */
    private String extractBaseUrl(String url) {
        try {
            java.net.URL urlObj = new java.net.URL(url);
            return String.format("%s://%s", urlObj.getProtocol(), urlObj.getHost());
        } catch (Exception e) {
            logger.warn("Could not extract base URL from: {}", url);
            return null;
        }
    }
}


