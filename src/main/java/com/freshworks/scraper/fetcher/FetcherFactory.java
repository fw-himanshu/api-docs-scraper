package com.freshworks.scraper.fetcher;

/**
 * Factory for creating appropriate page fetchers.
 */
public class FetcherFactory {
    
    public enum FetcherType {
        HTTP_CLIENT,
        PLAYWRIGHT
    }
    
    /**
     * Creates a page fetcher of the specified type.
     * 
     * @param type The type of fetcher to create
     * @return A new PageFetcher instance
     */
    public static PageFetcher createFetcher(FetcherType type) {
        return switch (type) {
            case HTTP_CLIENT -> new HttpClientFetcher();
            case PLAYWRIGHT -> new PlaywrightFetcher();
        };
    }
    
    /**
     * Creates a fetcher based on the URL or user preference.
     * Default is HTTP_CLIENT for better performance.
     * 
     * @param url The URL to fetch (can be used for heuristics)
     * @param forcePlaywright If true, always use Playwright
     * @return A new PageFetcher instance
     */
    public static PageFetcher createFetcher(String url, boolean forcePlaywright) {
        if (forcePlaywright) {
            return new PlaywrightFetcher();
        }
        
        // Heuristic: use Playwright for known JS-heavy frameworks
        if (url.contains("stoplight") || url.contains("redoc") || url.contains("swagger")) {
            return new PlaywrightFetcher();
        }
        
        return new HttpClientFetcher();
    }
}

