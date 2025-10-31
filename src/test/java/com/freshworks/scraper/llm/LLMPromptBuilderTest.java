package com.freshworks.scraper.llm;

import com.freshworks.scraper.model.Endpoint;
import com.freshworks.scraper.model.Example;
import com.freshworks.scraper.model.Parameter;
import com.freshworks.scraper.model.ScrapedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LLMPromptBuilderTest {

    private LLMPromptBuilder builder;
    private ScrapedResult result;

    @BeforeEach
    void setUp() {
        builder = new LLMPromptBuilder();
        result = new ScrapedResult("https://api.example.com/docs");
        
        // Create sample endpoints
        Endpoint endpoint1 = new Endpoint("GET", "/api/users");
        endpoint1.setSummary("Get all users");
        endpoint1.setDescription("Retrieves a list of all users in the system");
        
        Parameter param1 = new Parameter("page", "integer", false, "Page number");
        endpoint1.addParameter(param1);
        
        Example response1 = new Example("json", "{ \"users\": [] }");
        endpoint1.setResponseExample(response1);
        
        result.addEndpoint(endpoint1);
        
        Endpoint endpoint2 = new Endpoint("POST", "/api/users");
        endpoint2.setSummary("Create a user");
        
        Parameter param2 = new Parameter("name", "string", true, "User name");
        endpoint2.addParameter(param2);
        
        Example request2 = new Example("json", "{ \"name\": \"John\" }");
        endpoint2.setRequestExample(request2);
        
        result.addEndpoint(endpoint2);
    }

    @Test
    void testBuildPrompt() {
        String prompt = builder.buildPrompt(result);
        
        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());
        
        // Check that key information is included
        assertTrue(prompt.contains("api.example.com"));
        assertTrue(prompt.contains("GET"));
        assertTrue(prompt.contains("POST"));
        assertTrue(prompt.contains("/api/users"));
        assertTrue(prompt.contains("OpenAPI"));
        
        System.out.println("Generated Prompt:");
        System.out.println("=".repeat(80));
        System.out.println(prompt);
        System.out.println("=".repeat(80));
    }

    @Test
    void testBuildCompactPrompt() {
        String prompt = builder.buildCompactPrompt(result);
        
        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());
        assertTrue(prompt.contains("GET /api/users"));
        assertTrue(prompt.contains("POST /api/users"));
        
        System.out.println("Compact Prompt:");
        System.out.println(prompt);
    }

    @Test
    void testPromptContainsEndpointDetails() {
        String prompt = builder.buildPrompt(result);
        
        // Check for endpoint details
        assertTrue(prompt.contains("Get all users"));
        assertTrue(prompt.contains("Create a user"));
        assertTrue(prompt.contains("page"));
        assertTrue(prompt.contains("name"));
        assertTrue(prompt.contains("[REQUIRED]"));
    }

    @Test
    void testPromptContainsExamples() {
        String prompt = builder.buildPrompt(result);
        
        // Check for examples
        assertTrue(prompt.contains("{ \"users\": [] }"));
        assertTrue(prompt.contains("{ \"name\": \"John\" }"));
    }
}

