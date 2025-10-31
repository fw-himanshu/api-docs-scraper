package com.freshworks.scraper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Spring Boot application entry point for the API documentation scraper.
 * 
 * This application provides:
 * - REST API endpoint for scraping API documentation
 * - CLI support for command-line usage
 * - Integrated LLM-powered intelligent extraction
 */
@SpringBootApplication
public class Application extends SpringBootServletInitializer {
    
    public static void main(String[] args) {
        // Check if running as CLI
        if (args.length > 0 && isCLIMode(args)) {
            // Run as CLI
            com.freshworks.scraper.cli.ScraperCLI.main(args);
        } else {
            // Run as Spring Boot web application
            SpringApplication.run(Application.class, args);
        }
    }
    
    /**
     * Determines if the arguments indicate CLI mode.
     * CLI mode if: --url, --help, --version, or any URL-like arguments are present
     */
    private static boolean isCLIMode(String[] args) {
        String joined = String.join(" ", args);
        return joined.contains("--url") || 
               joined.contains("--help") || 
               joined.contains("--version") ||
               joined.contains("://");
    }
}

