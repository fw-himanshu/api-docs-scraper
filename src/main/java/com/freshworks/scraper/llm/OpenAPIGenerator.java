package com.freshworks.scraper.llm;

import com.freshworks.scraper.model.Endpoint;
import com.freshworks.scraper.model.Parameter;
import com.freshworks.scraper.model.ScrapedResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates OpenAPI 3.0 specification from scraped endpoints using LLM.
 */
public class OpenAPIGenerator {
    private static final Logger logger = LoggerFactory.getLogger(OpenAPIGenerator.class);
    
    private final LLMClient llmClient;
    private final Gson gson;
    
    public OpenAPIGenerator(LLMClient llmClient) {
        this.llmClient = llmClient;
        this.gson = new Gson();
    }
    
    /**
     * Generates OpenAPI 3.0 specification for the scraped result.
     * Splits endpoints into chunks to avoid truncation, then merges the results.
     * 
     * @param result The scraped result containing endpoints
     * @param baseUrl Base URL for the API (extracted from source URL)
     * @return OpenAPI 3.0 specification as JSON string
     * @throws LLMException if generation fails
     */
    public String generateOpenAPI(ScrapedResult result, String baseUrl) throws LLMException {
        logger.info("🤖 Generating OpenAPI 3.0 specification...");
        logger.info("   📊 Endpoints: {}", result.getEndpoints().size());
        logger.info("   🔗 Source: {}", result.getSource());
        
        // Determine base URL
        String apiBaseUrl = determineBaseUrl(result.getSource(), baseUrl);
        
        // Check if we need to split into chunks
        int chunkSize = 8; // Generate specs for 8 endpoints at a time
        
        if (result.getEndpoints().size() <= chunkSize) {
            // Small enough to generate in one go
            logger.info("   🔄 Generating complete spec in single request");
            return generateCompleteOpenAPI(result.getEndpoints(), apiBaseUrl, result.getSource());
        } else {
            // Split into chunks and merge
            logger.info("   🔄 Generating spec in parts ({} endpoints per chunk)", chunkSize);
            return generateOpenAPIInChunks(result.getEndpoints(), apiBaseUrl, result.getSource(), chunkSize);
        }
    }
    
    /**
     * Generates complete OpenAPI spec for small endpoint lists.
     */
    private String generateCompleteOpenAPI(List<Endpoint> endpoints, String baseUrl, String source) throws LLMException {
        String endpointsJson = prepareEndpointsForLLM(endpoints);
        
        String systemPrompt = "You are an expert in OpenAPI 3.0 specification. " +
                "Generate a COMPLETE, valid OpenAPI 3.0 specification in JSON format. " +
                "Return ONLY valid JSON, no markdown, no code blocks.";
        
        String userPrompt = String.format(
                "Generate a complete OpenAPI 3.0 specification for these %d API endpoints.\n\n" +
                "Base URL: %s\n\n" +
                "Endpoints:\n%s\n\n" +
                "Requirements:\n" +
                "1. openapi: 3.0.0\n" +
                "2. info section with title and version\n" +
                "3. servers section with base URL: %s\n" +
                "4. paths section with all endpoints\n" +
                "5. components section with schemas\n\n" +
                "Return ONLY valid JSON.",
                endpoints.size(), baseUrl, endpointsJson, baseUrl
        );
        
        logger.info("   🤖 Calling LLM to generate complete OpenAPI spec...");
        long startTime = System.currentTimeMillis();
        
        try {
            String response = llmClient.call(systemPrompt, userPrompt);
            String cleanResponse = cleanOpenAPIResponse(response);
            validateOpenAPISpec(cleanResponse, endpoints.size());
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("   ✅ Complete OpenAPI generation completed in {} ms", duration);
            
            return cleanResponse;
            
        } catch (Exception e) {
            logger.error("   ❌ Failed to generate OpenAPI spec", e);
            throw new LLMException("Failed to generate OpenAPI spec: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generates OpenAPI spec in chunks and merges them together.
     */
    private String generateOpenAPIInChunks(List<Endpoint> endpoints, String baseUrl, String source, int chunkSize) throws LLMException {
        logger.info("   📦 Splitting {} endpoints into chunks of {}", endpoints.size(), chunkSize);
        
        // Split endpoints into chunks
        List<List<Endpoint>> chunks = splitIntoChunks(endpoints, chunkSize);
        logger.info("   📊 Created {} chunks", chunks.size());
        
        // Generate spec for each chunk
        List<JsonObject> chunkSpecs = new ArrayList<>();
        int chunkNum = 0;
        
        for (List<Endpoint> chunk : chunks) {
            chunkNum++;
            logger.info("   🔄 Processing chunk {}/{} ({} endpoints)", chunkNum, chunks.size(), chunk.size());
            
            try {
                String chunkJson = generateSingleChunkSpec(chunk, baseUrl, chunkNum);
                JsonObject chunkSpec = gson.fromJson(chunkJson, JsonObject.class);
                chunkSpecs.add(chunkSpec);
                logger.info("   ✅ Chunk {}/{} completed", chunkNum, chunks.size());
            } catch (Exception e) {
                logger.error("   ❌ Failed to generate chunk {}/{}", chunkNum, chunks.size(), e);
                // Continue with remaining chunks
            }
        }
        
        if (chunkSpecs.isEmpty()) {
            throw new LLMException("Failed to generate any OpenAPI spec chunks");
        }
        
        // Merge all chunks into one complete spec
        logger.info("   🔗 Merging {} chunk specs...", chunkSpecs.size());
        return mergeOpenAPISpecs(chunkSpecs, baseUrl, source);
    }
    
    /**
     * Generates OpenAPI spec for a single chunk of endpoints.
     */
    private String generateSingleChunkSpec(List<Endpoint> endpoints, String baseUrl, int chunkNum) throws LLMException {
        String endpointsJson = prepareEndpointsForLLM(endpoints);
        
        String systemPrompt = "You are an expert in OpenAPI 3.0 specification. " +
                "You are generating a PART of a larger OpenAPI spec. " +
                "Return ONLY the paths section for the endpoints provided. " +
                "Return valid JSON with just the paths object: {\"paths\": {...}}. " +
                "No openapi version, no info, no servers - just the paths.";
        
        String userPrompt = String.format(
                "Generate the paths section for these %d API endpoints (chunk %d).\n\n" +
                "Base URL: %s\n\n" +
                "Endpoints:\n%s\n\n" +
                "Requirements:\n" +
                "1. Return JSON with ONLY a 'paths' object\n" +
                "2. Include all endpoints with their methods, summary, description, parameters, and responses\n" +
                "3. Use proper OpenAPI 3.0 format\n" +
                "4. Example structure: {\"paths\": {\"/users\": {\"get\": {...}}}\n\n" +
                "Return ONLY valid JSON with the paths object.",
                endpoints.size(), chunkNum, baseUrl, endpointsJson
        );
        
        try {
            String response = llmClient.call(systemPrompt, userPrompt);
            return cleanOpenAPIResponse(response);
        } catch (LLMException e) {
            logger.error("Failed to generate chunk {}", chunkNum, e);
            throw e;
        }
    }
    
    /**
     * Splits a list of endpoints into chunks.
     */
    private List<List<Endpoint>> splitIntoChunks(List<Endpoint> endpoints, int chunkSize) {
        List<List<Endpoint>> chunks = new ArrayList<>();
        
        for (int i = 0; i < endpoints.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, endpoints.size());
            chunks.add(endpoints.subList(i, end));
        }
        
        return chunks;
    }
    
    /**
     * Merges multiple OpenAPI spec chunks into one complete spec.
     */
    private String mergeOpenAPISpecs(List<JsonObject> chunkSpecs, String baseUrl, String source) throws LLMException {
        logger.info("   🔗 Starting merge of {} chunk specs", chunkSpecs.size());
        
        // Create the complete OpenAPI spec structure
        JsonObject merged = new JsonObject();
        merged.addProperty("openapi", "3.0.0");
        
        // Add info section
        JsonObject info = new JsonObject();
        info.addProperty("title", "API Documentation");
        info.addProperty("version", "1.0.0");
        info.addProperty("description", "Generated from " + source);
        merged.add("info", info);
        
        // Add servers section
        JsonArray servers = new JsonArray();
        JsonObject server = new JsonObject();
        server.addProperty("url", baseUrl);
        servers.add(server);
        merged.add("servers", servers);
        
        // Merge all paths from chunks
        JsonObject allPaths = new JsonObject();
        int totalPaths = 0;
        
        for (JsonObject chunkSpec : chunkSpecs) {
            if (chunkSpec.has("paths")) {
                JsonObject paths = chunkSpec.getAsJsonObject("paths");
                for (String pathKey : paths.keySet()) {
                    allPaths.add(pathKey, paths.get(pathKey));
                    totalPaths++;
                }
            }
        }
        
        merged.add("paths", allPaths);
        
        // Add components section
        JsonObject components = new JsonObject();
        components.add("schemas", new JsonObject());
        merged.add("components", components);
        
        logger.info("   ✅ Merged spec complete: {} paths total", totalPaths);
        logger.info("   📏 Merged spec length: {} characters", merged.toString().length());
        
        return gson.toJson(merged);
    }
    
    /**
     * Validates the generated OpenAPI spec.
     */
    private void validateOpenAPISpec(String response, int expectedEndpoints) {
        try {
            JsonObject spec = gson.fromJson(response, JsonObject.class);
            
            int pathsCount = spec.has("paths") ? spec.getAsJsonObject("paths").size() : 0;
            logger.info("   📊 Paths found in spec: {}", pathsCount);
            
            if (pathsCount < expectedEndpoints) {
                logger.warn("   ⚠️  Spec may be incomplete. Expected {} endpoints, found {} paths", 
                        expectedEndpoints, pathsCount);
            } else {
                logger.info("   ✅ Spec validation passed");
            }
            
        } catch (Exception e) {
            logger.error("   ❌ Spec validation failed: {}", e.getMessage());
        }
    }
    
    /**
     * Prepares endpoints data for LLM processing.
     */
    private String prepareEndpointsForLLM(List<Endpoint> endpoints) {
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < endpoints.size(); i++) {
            Endpoint ep = endpoints.get(i);
            
            sb.append(String.format("\n%d. %s %s", i + 1, ep.getMethod(), ep.getPath()));
            
            if (ep.getSummary() != null && !ep.getSummary().isEmpty()) {
                sb.append(String.format("\n   Summary: %s", ep.getSummary()));
            }
            
            if (ep.getDescription() != null && !ep.getDescription().isEmpty()) {
                sb.append(String.format("\n   Description: %s", ep.getDescription()));
            }
            
            if (!ep.getParameters().isEmpty()) {
                sb.append("\n   Parameters:");
                for (Parameter param : ep.getParameters()) {
                    sb.append(String.format("\n     - %s (%s%s): %s",
                            param.getName(),
                            param.getType(),
                            param.isRequired() ? ", required" : ", optional",
                            param.getDescription() != null ? param.getDescription() : ""));
                }
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Determines the base URL for the API from the source URL.
     */
    private String determineBaseUrl(String sourceUrl, String providedBaseUrl) {
        if (providedBaseUrl != null && !providedBaseUrl.isEmpty()) {
            return providedBaseUrl;
        }
        
        // Try to extract base URL from source URL
        try {
            java.net.URL url = new java.net.URL(sourceUrl);
            return String.format("%s://%s", url.getProtocol(), url.getHost());
        } catch (Exception e) {
            logger.warn("Could not parse base URL from: {}", sourceUrl);
            return "https://api.example.com";
        }
    }
    
    /**
     * Cleans the OpenAPI response from the LLM.
     */
    private String cleanOpenAPIResponse(String response) {
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
        
        // Remove any YAML markers if present
        if (clean.startsWith("---")) {
            int index = clean.indexOf("\n");
            if (index > 0) {
                clean = clean.substring(index + 1);
            }
        }
        
        return clean.trim();
    }
    
    /**
     * Attempts to fix a truncated JSON response by adding closing braces.
     * This is a best-effort approach to salvage incomplete responses.
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
            
            // If we're in the middle of a string or object, try to close it
            if (inString) {
                // Find the last meaningful position before the string was cut
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
                gson.fromJson(result, JsonObject.class);
                logger.info("   ✅ Fixed JSON is now valid");
                logger.info("   📏 Fixed length: {} characters (was {})", result.length(), json.length());
                return result;
            } catch (Exception e) {
                logger.warn("   ⚠️  Fixed JSON is still invalid: {}", e.getMessage());
                return null;
            }
            
        } catch (Exception e) {
            logger.error("   ❌ Failed to fix truncated JSON: {}", e.getMessage());
            return null;
        }
    }
}

