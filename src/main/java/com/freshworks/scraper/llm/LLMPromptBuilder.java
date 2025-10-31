package com.freshworks.scraper.llm;

import com.freshworks.scraper.model.Endpoint;
import com.freshworks.scraper.model.Parameter;
import com.freshworks.scraper.model.ScrapedResult;

/**
 * Builds prompts for LLMs to generate OpenAPI specifications from scraped data.
 */
public class LLMPromptBuilder {

    private static final String PROMPT_TEMPLATE = """
            You are an API documentation expert. Based on the following scraped API endpoint information,
            generate a complete OpenAPI 3.0 specification in YAML format.
            
            ## Source Information
            - Source URL: %s
            - Number of endpoints: %d
            - Scraped at: %s
            
            ## Scraped Endpoints
            
            %s
            
            ## Instructions
            1. Generate a valid OpenAPI 3.0 specification
            2. Include appropriate schemas for request/response bodies based on the examples
            3. Add security schemes if authentication patterns are detected
            4. Group endpoints by tags/resources
            5. Ensure all paths, methods, parameters, and responses are properly documented
            6. Use the examples provided to infer data types and structures
            
            Please provide the complete OpenAPI specification:
            """;

    /**
     * Builds a prompt for generating OpenAPI specification.
     * 
     * @param result The scraped result containing endpoints
     * @return A formatted prompt string for an LLM
     */
    public String buildPrompt(ScrapedResult result) {
        StringBuilder endpointsInfo = new StringBuilder();
        
        for (int i = 0; i < result.getEndpoints().size(); i++) {
            Endpoint endpoint = result.getEndpoints().get(i);
            endpointsInfo.append(formatEndpoint(i + 1, endpoint));
            endpointsInfo.append("\n");
        }
        
        return String.format(
            PROMPT_TEMPLATE,
            result.getSource(),
            result.getEndpoints().size(),
            result.getScrapedAt(),
            endpointsInfo.toString()
        );
    }

    /**
     * Builds a concise prompt with just the essential information.
     * 
     * @param result The scraped result
     * @return A compact prompt string
     */
    public String buildCompactPrompt(ScrapedResult result) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate OpenAPI 3.0 spec for these endpoints:\n\n");
        
        for (Endpoint endpoint : result.getEndpoints()) {
            prompt.append(String.format("- %s %s", endpoint.getMethod(), endpoint.getPath()));
            if (endpoint.getSummary() != null) {
                prompt.append(" - ").append(endpoint.getSummary());
            }
            prompt.append("\n");
        }
        
        return prompt.toString();
    }

    private String formatEndpoint(int index, Endpoint endpoint) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("### Endpoint ").append(index).append("\n");
        sb.append("**Method:** ").append(endpoint.getMethod()).append("\n");
        sb.append("**Path:** ").append(endpoint.getPath()).append("\n");
        
        if (endpoint.getSummary() != null && !endpoint.getSummary().isEmpty()) {
            sb.append("**Summary:** ").append(endpoint.getSummary()).append("\n");
        }
        
        if (endpoint.getDescription() != null && !endpoint.getDescription().isEmpty()) {
            sb.append("**Description:** ").append(endpoint.getDescription()).append("\n");
        }
        
        if (!endpoint.getTags().isEmpty()) {
            sb.append("**Tags:** ").append(String.join(", ", endpoint.getTags())).append("\n");
        }
        
        // Parameters
        if (!endpoint.getParameters().isEmpty()) {
            sb.append("\n**Parameters:**\n");
            for (Parameter param : endpoint.getParameters()) {
                sb.append("  - `").append(param.getName()).append("`");
                sb.append(" (").append(param.getType()).append(")");
                if (param.isRequired()) {
                    sb.append(" [REQUIRED]");
                }
                if (param.getDescription() != null && !param.getDescription().isEmpty()) {
                    sb.append(": ").append(param.getDescription());
                }
                sb.append("\n");
            }
        }
        
        // Request example
        if (endpoint.getRequestExample() != null) {
            sb.append("\n**Request Example (").append(endpoint.getRequestExample().getLanguage()).append("):**\n");
            sb.append("```\n");
            sb.append(endpoint.getRequestExample().getCode());
            sb.append("\n```\n");
        }
        
        // Response example
        if (endpoint.getResponseExample() != null) {
            sb.append("\n**Response Example (").append(endpoint.getResponseExample().getLanguage()).append("):**\n");
            sb.append("```\n");
            sb.append(endpoint.getResponseExample().getCode());
            sb.append("\n```\n");
        }
        
        sb.append("\n---\n");
        
        return sb.toString();
    }

    /**
     * Saves the prompt to a file for later use.
     * 
     * @param result The scraped result
     * @param outputPath Path to save the prompt
     */
    public void savePromptToFile(ScrapedResult result, String outputPath) {
        String prompt = buildPrompt(result);
        try {
            java.nio.file.Files.writeString(
                java.nio.file.Paths.get(outputPath),
                prompt
            );
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to save prompt to file", e);
        }
    }
}

