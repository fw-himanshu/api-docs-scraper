package com.freshworks.scraper;

/**
 * Exception thrown when scraping operations fail.
 */
public class ScraperException extends Exception {
    public ScraperException(String message) {
        super(message);
    }

    public ScraperException(String message, Throwable cause) {
        super(message, cause);
    }
}

