package com.freshworks.scraper.fetcher;

/**
 * Exception thrown when page fetching fails.
 */
public class FetchException extends Exception {
    public FetchException(String message) {
        super(message);
    }

    public FetchException(String message, Throwable cause) {
        super(message, cause);
    }
}

