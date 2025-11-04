package com.freshworks.scraper.parser;

import com.freshworks.scraper.llm.LLMClient;
import com.freshworks.scraper.llm.LLMException;
import com.freshworks.scraper.model.Endpoint;
import com.freshworks.scraper.model.Parameter;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for extracting API endpoints from README.md files.
 * Uses LLM to intelligently extract endpoints from markdown documentation.
 */
public class ReadmeParser implements DocumentParser {
    private static final Logger logger = LoggerFactory.getLogger(ReadmeParser.class);
    
    private final LLMClient llmClient;
    private final Gson gson;
    
    public ReadmeParser(LLMClient llmClient) {
        this.llmClient = llmClient;
        this.gson = new Gson();
    }
    
    @Override
    public List<Endpoint> parse(String content, String sourceUrl) {
        logger.info("Parsing README.md file: {}", sourceUrl);
        
        List<Endpoint> endpoints = new ArrayList<>();
        
        try {
            // Extract text content from markdown
            String markdownText = extractMarkdownText(content);
            logger.info("Extracted {} characters of markdown content", markdownText.length());
            
            // Use LLM to extract endpoints from the markdown
            List<Endpoint> extractedEndpoints = extractEndpointsFromMarkdown(markdownText, sourceUrl);
            endpoints.addAll(extractedEndpoints);
            
            logger.info("Extracted {} endpoints from README.md", endpoints.size());
            
        } catch (Exception e) {
            logger.error("Error parsing README.md", e);
        }
        
        return endpoints;
    }
    
    /**
     * Extracts text content from markdown or HTML (GitHub renders README as HTML).
     */
    private String extractMarkdownText(String content) {
        // Check if it's HTML (GitHub renders README)
        if (content.trim().startsWith("<!DOCTYPE") || content.trim().startsWith("<html")) {
            logger.info("Content is HTML rendered by GitHub, extracting text...");
            Document doc = Jsoup.parse(content);
            return doc.body().text();
        }
        
        // It's raw markdown
        logger.info("Content is raw markdown");
        return content;
    }
    
    /**
     * Uses LLM to extract endpoints from markdown text.
     * Passes URL context to LLM for better extraction.
     */
    private List<Endpoint> extractEndpointsFromMarkdown(String markdown, String sourceUrl) throws LLMException {
        logger.info("Using LLM to extract endpoints from markdown...");
        logger.info("Source URL: {}", sourceUrl);
        
        // Limit content size for LLM
        String limitedMarkdown = limitContentSize(markdown, 15000);
        
        // Determine URL type and provide context
        String urlContext = determineUrlContext(sourceUrl);
        
        String systemPrompt = "You are an expert at extracting API endpoints from README.md files and other markdown documentation. " +
                "Your task is to identify ALL API endpoints mentioned in the documentation AND extract the actual API server/base URL. " +
                "Look for API server URLs in the documentation (e.g., 'https://api.example.com', 'Base URL: https://api.quotable.io'). " +
                "Return a JSON array with this structure: [{\"method\": \"GET\", \"path\": \"/users\", \"summary\": \"Get users\", \"description\": \"...\", \"parameters\": [{\"name\": \"id\", \"type\": \"string\", \"required\": true}]}]. " +
                "If endpoints contain full URLs (e.g., 'https://api.quotable.io/quotes'), extract the base URL and use relative paths. " +
                "Return ONLY valid JSON, no markdown, no code blocks.";
        
        String userPrompt = String.format(
                "Extract ALL API endpoints from this markdown documentation.\n\n" +
                "**Source URL:** %s\n" +
                "**URL Context:** %s\n\n" +
                "IMPORTANT: Extract the ACTUAL API server URL from the documentation content. " +
                "Look for:\n" +
                "- API base URLs mentioned in the documentation (e.g., 'Base URL: https://api.quotable.io')\n" +
                "- Full URLs in endpoint examples (e.g., 'https://api.quotable.io/quotes' -> extract 'https://api.quotable.io')\n" +
                "- API documentation mentions (e.g., 'API available at https://api.example.com')\n\n" +
                "For endpoint paths:\n" +
                "- If documentation shows full URLs like 'https://api.quotable.io/quotes', extract '/quotes' as the path\n" +
                "- If documentation shows relative paths like '/api/quotes', use that as-is\n" +
                "- If paths are shown without leading slash, add one\n\n" +
                "**Markdown Content:**\n%s\n\n" +
                "Extract ALL API endpoints with their:\n" +
                "- HTTP method (GET, POST, PUT, DELETE, etc.)\n" +
                "- Complete path (relative path, not full URL)\n" +
                "- Summary/description\n" +
                "- Parameters (name, type, required, description)\n\n" +
                "Return a JSON array with ALL endpoints.",
                sourceUrl,
                urlContext,
                limitedMarkdown
        );
        
        logger.info("Calling LLM with URL context...");
        String response = llmClient.call(systemPrompt, userPrompt);
        
        return parseEndpointListResponse(response);
    }
    
    /**
     * Determines URL context information to help LLM understand the source better.
     */
    private String determineUrlContext(String sourceUrl) {
        String lowerUrl = sourceUrl.toLowerCase();
        StringBuilder context = new StringBuilder();
        
        if (lowerUrl.contains("github.com")) {
            context.append("GitHub README - ");
        }
        if (lowerUrl.contains("raw.githubusercontent.com")) {
            context.append("Raw markdown file - ");
        }
        if (lowerUrl.contains("gitlab")) {
            context.append("GitLab - ");
        }
        if (lowerUrl.contains("bitbucket")) {
            context.append("Bitbucket - ");
        }
        if (lowerUrl.contains("docs.")) {
            context.append("Documentation site - ");
        }
        
        // Extract domain
        try {
            java.net.URL url = new java.net.URL(sourceUrl);
            context.append("Domain: ").append(url.getHost());
        } catch (Exception e) {
            context.append("URL: ").append(sourceUrl);
        }
        
        return context.toString();
    }
    
    /**
     * Parses the LLM response containing endpoint list.
     */
    private List<Endpoint> parseEndpointListResponse(String response) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Clean the response
        String cleanResponse = cleanJsonResponse(response);
        logger.info("Parsing endpoint list response: {} characters", cleanResponse.length());
        
        try {
            JsonArray jsonArray = gson.fromJson(cleanResponse, JsonArray.class);
            endpoints = parseJsonArrayToEndpoints(jsonArray);
            logger.info("Parsed {} endpoints from LLM response", endpoints.size());
            
        } catch (com.google.gson.JsonSyntaxException e) {
            // Check if this is a truncation issue
            String errorMsg = e.getMessage();
            logger.error("Failed to parse JSON response");
            logger.error("Error: {}", errorMsg);
            
            if (errorMsg != null && errorMsg.contains("End of input")) {
                logger.error("CRITICAL: Response was TRUNCATED by LLM");
                logger.error("Attempting to fix truncated JSON...");
                
                // Try to fix truncated JSON
                String fixedResponse = attemptFixTruncatedJson(cleanResponse);
                if (fixedResponse != null) {
                    logger.info("Successfully fixed truncated JSON");
                    try {
                        JsonArray jsonArray = gson.fromJson(fixedResponse, JsonArray.class);
                        endpoints = parseJsonArrayToEndpoints(jsonArray);
                        logger.info("Parsed {} endpoints from fixed response", endpoints.size());
                    } catch (Exception e2) {
                        logger.error("Fixed JSON is still invalid", e2);
                    }
                } else {
                    logger.error("Could not fix truncated JSON");
                }
            } else {
                logger.error("JSON parsing error (not truncation): {}", errorMsg);
                logger.error("Response preview: {}", cleanResponse.substring(0, Math.min(500, cleanResponse.length())));
            }
        } catch (Exception e) {
            logger.error("Failed to parse endpoint list response", e);
            logger.error("Response length: {}", response != null ? response.length() : 0);
        }
        
        return endpoints;
    }
    
    /**
     * Parses a JsonArray into a list of Endpoint objects.
     */
    private List<Endpoint> parseJsonArrayToEndpoints(JsonArray jsonArray) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        for (JsonElement element : jsonArray) {
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                    
                String method = getStringOrNull(obj, "method");
                String path = getStringOrNull(obj, "path");
                
                if (method != null && path != null) {
                    Endpoint endpoint = new Endpoint(method.toUpperCase(), path);
                    endpoint.setSummary(getStringOrNull(obj, "summary"));
                    endpoint.setDescription(getStringOrNull(obj, "description"));
                    
                    // Parse parameters
                    if (obj.has("parameters") && obj.get("parameters").isJsonArray()) {
                        JsonArray params = obj.getAsJsonArray("parameters");
                        for (JsonElement paramElement : params) {
                            if (paramElement.isJsonObject()) {
                                JsonObject paramObj = paramElement.getAsJsonObject();
                                String paramName = getStringOrNull(paramObj, "name");
                                String paramType = getStringOrNull(paramObj, "type");
                                boolean required = paramObj.has("required") && paramObj.get("required").getAsBoolean();
                                String paramDesc = getStringOrNull(paramObj, "description");
                                
                                if (paramName != null) {
                                    Parameter param = new Parameter(paramName, paramType != null ? paramType : "string",
                                                                  required, paramDesc);
                                    endpoint.addParameter(param);
                                }
                            }
                        }
                    }
                    
                    endpoints.add(endpoint);
                }
                }
            }
            
            return endpoints;
    }
    
    /**
     * Attempts to fix a truncated JSON response by adding closing braces and brackets.
     */
    private String attemptFixTruncatedJson(String json) {
        try {
            // Count unmatched braces and brackets
            int openBraces = 0;
            int openBrackets = 0;
            boolean inString = false;
            boolean escaped = false;
            
            for (char c : json.toCharArray()) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                
                if (c == '"' && !escaped) {
                    inString = !inString;
                    continue;
                }
                
                if (inString) {
                    continue;
                }
                
                switch (c) {
                    case '{': openBraces++; break;
                    case '}': openBraces--; break;
                    case '[': openBrackets++; break;
                    case ']': openBrackets--; break;
                }
            }
            
            // If we're in the middle of a string or incomplete object, try to close it
            if (inString) {
                // Find the last meaningful position
                int lastComma = json.lastIndexOf(',');
                if (lastComma > 0) {
                    json = json.substring(0, lastComma);
                }
            }
            
            // Build the closing braces
            StringBuilder fixed = new StringBuilder(json.trim());
            
            // Remove trailing commas if they exist
            while (fixed.length() > 0 && (fixed.charAt(fixed.length() - 1) == ',' || 
                   fixed.charAt(fixed.length() - 1) == ' ')) {
                fixed.setLength(fixed.length() - 1);
            }
            
            // Close arrays
            while (openBrackets > 0) {
                fixed.append(']');
                openBrackets--;
            }
            
            // Close objects
            while (openBraces > 0) {
                fixed.append('}');
                openBraces--;
            }
            
            String result = fixed.toString();
            
            // Try to validate the fixed JSON
            try {
                gson.fromJson(result, JsonArray.class);
                logger.info("Fixed JSON is now valid");
                logger.info("Fixed length: {} characters (was {})", result.length(), json.length());
                return result;
            } catch (Exception e) {
                logger.warn("Fixed JSON is still invalid: {}", e.getMessage());
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Failed to fix truncated JSON: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Limits content size for LLM processing.
     */
    private String limitContentSize(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        
        logger.info("Content too long ({} chars), truncating to {} chars", content.length(), maxLength);
        return content.substring(0, maxLength) + "... (truncated)";
    }
    
    /**
     * Cleans JSON response from LLM.
     */
    private String cleanJsonResponse(String response) {
        String clean = response.trim();
        
        // Remove markdown code blocks
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        
        return clean.trim();
    }
    
    /**
     * Helper to safely get string or null from JsonObject.
     */
    private String getStringOrNull(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }
}

