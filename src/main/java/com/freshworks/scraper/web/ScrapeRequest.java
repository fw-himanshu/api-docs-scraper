package com.freshworks.scraper.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO for the scrape API endpoint.
 */
public class ScrapeRequest {
    
    @NotBlank(message = "URL is required")
    private String url;
    
    private String outputPath;
    private boolean usePlaywright = false;
    private boolean verbose = false;
    private String llmToken;
    private List<String> additionalUrls;
    
    public ScrapeRequest() {
    }
    
    public ScrapeRequest(String url) {
        this.url = url;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getOutputPath() {
        return outputPath;
    }
    
    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }
    
    public boolean isUsePlaywright() {
        return usePlaywright;
    }
    
    public void setUsePlaywright(boolean usePlaywright) {
        this.usePlaywright = usePlaywright;
    }
    
    public boolean isVerbose() {
        return verbose;
    }
    
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
    
    public String getLlmToken() {
        return llmToken;
    }
    
    public void setLlmToken(String llmToken) {
        this.llmToken = llmToken;
    }
    
    public List<String> getAdditionalUrls() {
        return additionalUrls;
    }
    
    public void setAdditionalUrls(List<String> additionalUrls) {
        this.additionalUrls = additionalUrls;
    }
}


