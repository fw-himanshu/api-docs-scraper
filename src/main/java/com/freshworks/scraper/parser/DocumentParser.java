package com.freshworks.scraper.parser;

import com.freshworks.scraper.model.Endpoint;
import java.util.List;

/**
 * Interface for parsing HTML documentation into structured endpoints.
 */
public interface DocumentParser {
    /**
     * Parses HTML content and extracts API endpoints.
     * 
     * @param html The HTML content to parse
     * @param sourceUrl The source URL (for context)
     * @return List of extracted endpoints
     */
    List<Endpoint> parse(String html, String sourceUrl);
}

