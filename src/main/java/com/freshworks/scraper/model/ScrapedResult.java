package com.freshworks.scraper.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Container for all scraped data from a documentation source.
 */
public class ScrapedResult {
    private String source;
    private LocalDateTime scrapedAt;
    private List<Endpoint> endpoints;
    private String baseUrl;
    private String version;

    public ScrapedResult() {
        this.endpoints = new ArrayList<>();
        this.scrapedAt = LocalDateTime.now();
    }

    public ScrapedResult(String source) {
        this();
        this.source = source;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getScrapedAt() {
        return scrapedAt;
    }

    public void setScrapedAt(LocalDateTime scrapedAt) {
        this.scrapedAt = scrapedAt;
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public void addEndpoint(Endpoint endpoint) {
        this.endpoints.add(endpoint);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "ScrapedResult{" +
                "source='" + source + '\'' +
                ", endpoints=" + endpoints.size() +
                ", scrapedAt=" + scrapedAt +
                '}';
    }
}

