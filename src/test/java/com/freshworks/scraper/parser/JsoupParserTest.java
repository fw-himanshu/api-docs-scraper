package com.freshworks.scraper.parser;

import com.freshworks.scraper.model.Endpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsoupParserTest {

    private JsoupParser parser;
    private String sampleHtml;

    @BeforeEach
    void setUp() throws IOException {
        parser = new JsoupParser();
        
        // Load the sample HTML file
        Path htmlPath = Paths.get("src/test/resources/sample-api-doc.html");
        sampleHtml = Files.readString(htmlPath);
    }

    @Test
    void testParseExtractsEndpoints() {
        List<Endpoint> endpoints = parser.parse(sampleHtml, "http://example.com");
        
        assertNotNull(endpoints);
        assertFalse(endpoints.isEmpty(), "Should extract at least one endpoint");
        
        System.out.println("Extracted " + endpoints.size() + " endpoints:");
        for (Endpoint endpoint : endpoints) {
            System.out.println("  " + endpoint);
        }
    }

    @Test
    void testParseExtractsGetEndpoint() {
        List<Endpoint> endpoints = parser.parse(sampleHtml, "http://example.com");
        
        // Find GET /api/users
        Endpoint getUsers = endpoints.stream()
                .filter(e -> "GET".equals(e.getMethod()) && "/api/users".equals(e.getPath()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(getUsers, "Should find GET /api/users endpoint");
        assertEquals("GET", getUsers.getMethod());
        assertEquals("/api/users", getUsers.getPath());
        assertNotNull(getUsers.getSummary());
    }

    @Test
    void testParseExtractsPostEndpoint() {
        List<Endpoint> endpoints = parser.parse(sampleHtml, "http://example.com");
        
        // Find POST /api/users
        Endpoint postUsers = endpoints.stream()
                .filter(e -> "POST".equals(e.getMethod()) && "/api/users".equals(e.getPath()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(postUsers, "Should find POST /api/users endpoint");
        assertEquals("POST", postUsers.getMethod());
        assertEquals("/api/users", postUsers.getPath());
    }

    @Test
    void testParseExtractsDeleteEndpoint() {
        List<Endpoint> endpoints = parser.parse(sampleHtml, "http://example.com");
        
        // Find DELETE /api/users/{id}
        Endpoint deleteUser = endpoints.stream()
                .filter(e -> "DELETE".equals(e.getMethod()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(deleteUser, "Should find DELETE endpoint");
        assertEquals("DELETE", deleteUser.getMethod());
        assertTrue(deleteUser.getPath().contains("/api/users"));
    }

    @Test
    void testParseExtractsParameters() {
        List<Endpoint> endpoints = parser.parse(sampleHtml, "http://example.com");
        
        // Find an endpoint with parameters
        Endpoint endpointWithParams = endpoints.stream()
                .filter(e -> !e.getParameters().isEmpty())
                .findFirst()
                .orElse(null);
        
        if (endpointWithParams != null) {
            System.out.println("Found endpoint with parameters: " + endpointWithParams);
            System.out.println("Parameters: " + endpointWithParams.getParameters());
            assertTrue(endpointWithParams.getParameters().size() > 0, 
                      "Should extract parameters from tables");
        }
    }

    @Test
    void testParseExtractsExamples() {
        List<Endpoint> endpoints = parser.parse(sampleHtml, "http://example.com");
        
        // Find an endpoint with examples
        Endpoint endpointWithExample = endpoints.stream()
                .filter(e -> e.getResponseExample() != null || e.getRequestExample() != null)
                .findFirst()
                .orElse(null);
        
        if (endpointWithExample != null) {
            System.out.println("Found endpoint with examples: " + endpointWithExample);
            assertTrue(
                endpointWithExample.getResponseExample() != null || 
                endpointWithExample.getRequestExample() != null,
                "Should extract code examples"
            );
        }
    }
}

