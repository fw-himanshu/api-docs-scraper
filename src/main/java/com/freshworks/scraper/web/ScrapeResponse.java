package com.freshworks.scraper.web;

import com.freshworks.scraper.model.Endpoint;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for the scrape API endpoint.
 */
public class ScrapeResponse {
    
    private boolean success;
    private String message;
    private String sourceUrl;
    private LocalDateTime scrapedAt;
    private int endpointCount;
    private List<Endpoint> endpoints;
    private String jsonOutput;
    private String openApiSpec;  // OpenAPI 3.0 specification
    private String errorDetails;
    
    public ScrapeResponse() {
    }
    
    public ScrapeResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.scrapedAt = LocalDateTime.now();
    }
    
    public static ScrapeResponse success(String message, List<Endpoint> endpoints, String jsonOutput) {
        ScrapeResponse response = new ScrapeResponse(true, message);
        response.setEndpoints(endpoints);
        response.setEndpointCount(endpoints != null ? endpoints.size() : 0);
        response.setJsonOutput(jsonOutput);
        return response;
    }
    
    public static ScrapeResponse successWithOpenAPI(String message, List<Endpoint> endpoints, String openApiSpec) {
        ScrapeResponse response = new ScrapeResponse(true, message);
        response.setEndpoints(endpoints);
        response.setEndpointCount(endpoints != null ? endpoints.size() : 0);
        response.setOpenApiSpec(openApiSpec);
        return response;
    }
    
    public static ScrapeResponse failure(String message, String errorDetails) {
        ScrapeResponse response = new ScrapeResponse(false, message);
        response.setErrorDetails(errorDetails);
        return response;
    }
    
    // Getters and setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getSourceUrl() {
        return sourceUrl;
    }
    
    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }
    
    public LocalDateTime getScrapedAt() {
        return scrapedAt;
    }
    
    public void setScrapedAt(LocalDateTime scrapedAt) {
        this.scrapedAt = scrapedAt;
    }
    
    public int getEndpointCount() {
        return endpointCount;
    }
    
    public void setEndpointCount(int endpointCount) {
        this.endpointCount = endpointCount;
    }
    
    public List<Endpoint> getEndpoints() {
        return endpoints;
    }
    
    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }
    
    public String getJsonOutput() {
        return jsonOutput;
    }
    
    public void setJsonOutput(String jsonOutput) {
        this.jsonOutput = jsonOutput;
    }
    
    public String getOpenApiSpec() {
        return openApiSpec;
    }
    
    public void setOpenApiSpec(String openApiSpec) {
        this.openApiSpec = openApiSpec;
    }
    
    public String getErrorDetails() {
        return errorDetails;
    }
    
    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }
}


