package com.freshworks.scraper.fetcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Simple HTTP-based page fetcher for static HTML pages.
 */
public class HttpClientFetcher implements PageFetcher {
    private static final Logger logger = LoggerFactory.getLogger(HttpClientFetcher.class);
    private final HttpClient httpClient;

    public HttpClientFetcher() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String fetch(String url) throws FetchException {
        logger.info("Fetching URL with HttpClient: {}", url);
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "API-Docs-Scraper/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Successfully fetched URL: {} (status: {})", url, response.statusCode());
                return response.body();
            } else {
                throw new FetchException("HTTP error: " + response.statusCode());
            }
        } catch (Exception e) {
            logger.error("Failed to fetch URL: {}", url, e);
            throw new FetchException("Failed to fetch URL: " + url, e);
        }
    }

    @Override
    public void close() {
        // HttpClient doesn't require explicit closing
    }
}

