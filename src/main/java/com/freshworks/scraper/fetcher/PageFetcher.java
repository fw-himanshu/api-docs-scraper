package com.freshworks.scraper.fetcher;

/**
 * Interface for fetching web page content.
 */
public interface PageFetcher {
    /**
     * Fetches the HTML content of a page.
     * 
     * @param url The URL to fetch
     * @return The HTML content as a string
     * @throws FetchException if fetching fails
     */
    String fetch(String url) throws FetchException;
    
    /**
     * Closes any resources held by the fetcher.
     */
    void close();
}

