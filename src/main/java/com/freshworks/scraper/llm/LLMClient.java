package com.freshworks.scraper.llm;

import com.freshworks.scraper.config.AppConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client for calling the Freshworks LLM API for intelligent decision-making.
 */
public class LLMClient {
    private static final Logger logger = LoggerFactory.getLogger(LLMClient.class);
    
    private final String apiToken;
    private final String apiUrl;
    private final String model;
    private final double temperature;
    private final double topP;
    private final HttpClient httpClient;
    private final Gson gson;
    
    /**
     * Creates an LLM client with the provided API token.
     * Configuration is loaded from application.properties.
     * 
     * @param apiToken The API token for authentication
     */
    public LLMClient(String apiToken) {
        this(apiToken, new AppConfig());
    }
    
    /**
     * Creates an LLM client with the provided API token and configuration.
     * 
     * @param apiToken The API token for authentication
     * @param config Application configuration
     */
    public LLMClient(String apiToken, AppConfig config) {
        this.apiToken = apiToken;
        this.apiUrl = config.getLLMApiUrl();
        this.model = config.getLLMApiModel();
        this.temperature = config.getLLMApiTemperature();
        this.topP = config.getLLMApiTopP();
        
        int timeoutSeconds = config.getLLMApiTimeoutSeconds();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        this.gson = new Gson();
        
        logger.debug("LLM Client initialized with URL: {}, Model: {}", apiUrl, model);
    }
    
    /**
     * Calls the LLM API with a system prompt and user message.
     * 
     * @param systemPrompt The system prompt to set context
     * @param userPrompt The user's question or request
     * @return The LLM's response text
     * @throws LLMException if the API call fails
     */
    public String call(String systemPrompt, String userPrompt) throws LLMException {
        long startTime = System.currentTimeMillis();
        logger.info("🤖 Calling LLM API");
        logger.info("   📍 URL: {}", apiUrl);
        logger.info("   🔧 Model: {}", model);
        logger.info("   💬 Prompt length: {} characters", userPrompt.length());
        logger.info("   📝 Prompt preview: {}...", userPrompt.substring(0, Math.min(100, userPrompt.length())));
        
        try {
            JsonObject requestBody = buildRequestBody(systemPrompt, userPrompt);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();
            
            logger.info("   📤 Sending HTTP request to LLM...");
            long requestTime = System.currentTimeMillis();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            long responseTime = System.currentTimeMillis();
            long duration = responseTime - startTime;
            
            logger.info("   ⏱️  LLM response received in {} ms", duration);
            logger.info("   📊 HTTP Status: {}", response.statusCode());
            
            if (response.statusCode() == 200) {
                String result = extractResponseContent(response.body());
                logger.info("   ✅ LLM response extracted: {} characters", result.length());
                logger.debug("   📄 Response preview: {}", result.substring(0, Math.min(200, result.length())));
                
                // Check for potential truncation
                if (response.body().contains("\"finish_reason\":\"length\"")) {
                    logger.warn("   ⚠️  WARNING: Response may be truncated (finish_reason: length)");
                }
                
                // Log response details
                try {
                    JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                    if (responseJson.has("usage")) {
                        JsonObject usage = responseJson.getAsJsonObject("usage");
                        logger.info("   📊 Token usage: {}", usage);
                    }
                } catch (Exception e) {
                    // Ignore parsing errors for usage info
                }
                
                return result;
            } else {
                logger.error("   ❌ LLM API returned error status {}: {}", response.statusCode(), response.body());
                throw new LLMException("LLM API call failed with status: " + response.statusCode());
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("   ❌ Failed to call LLM API after {} ms", duration, e);
            throw new LLMException("Failed to call LLM API: " + e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to decide between multiple options using the LLM.
     * 
     * @param context Context about the decision
     * @param question The question to ask
     * @param options Available options
     * @return The LLM's recommended choice
     */
    public String decide(String context, String question, String... options) throws LLMException {
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Context: ").append(context).append("\n\n");
        userPrompt.append("Question: ").append(question).append("\n\n");
        userPrompt.append("Options:\n");
        for (int i = 0; i < options.length; i++) {
            userPrompt.append((i + 1)).append(". ").append(options[i]).append("\n");
        }
        userPrompt.append("\nPlease respond with only the number of your recommended option.");
        
        String systemPrompt = "You are an expert at analyzing API documentation and making intelligent decisions about data extraction. Provide concise, specific answers.";
        
        return call(systemPrompt, userPrompt.toString());
    }
    
    /**
     * Asks the LLM to extract specific information from HTML content.
     * 
     * @param htmlSnippet The HTML content
     * @param extractionTask What to extract
     * @return The extracted information
     */
    public String extract(String htmlSnippet, String extractionTask) throws LLMException {
        String systemPrompt = "You are an expert at extracting structured information from API documentation HTML. " +
                "Provide concise, accurate responses in the requested format.";
        
        String userPrompt = String.format(
                "Task: %s\n\nHTML Content:\n%s\n\nProvide the extracted information:",
                extractionTask,
                htmlSnippet
        );
        
        return call(systemPrompt, userPrompt);
    }
    
    private JsonObject buildRequestBody(String systemPrompt, String userPrompt) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", temperature);
        body.addProperty("top_p", topP);
        
        // Set high max_tokens to avoid truncation
        // For large OpenAPI specs, we need up to 8192 tokens
        body.addProperty("max_tokens", 8192);
        
        JsonArray messages = new JsonArray();
        
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);
        
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);
        
        body.add("messages", messages);
        
        return body;
    }
    
    private String extractResponseContent(String responseBody) {
        try {
            // First, try to parse as JSON object
            JsonObject response = gson.fromJson(responseBody, JsonObject.class);
            
            // Try different response formats
            if (response.has("choices")) {
                JsonArray choices = response.getAsJsonArray("choices");
                if (choices.size() > 0) {
                    JsonObject firstChoice = choices.get(0).getAsJsonObject();
                    if (firstChoice.has("message")) {
                        return firstChoice.getAsJsonObject("message").get("content").getAsString();
                    }
                }
            }
            
            if (response.has("content")) {
                return response.get("content").getAsString();
            }
            
            if (response.has("response")) {
                return response.get("response").getAsString();
            }
            
            // Return raw body if format is unknown
            return responseBody;
            
        } catch (com.google.gson.JsonSyntaxException e) {
            // If it's a JsonPrimitive (plain string), try to extract it
            try {
                String unquoted = gson.fromJson(responseBody, String.class);
                if (unquoted != null) {
                    logger.debug("Response was a plain string, extracted successfully");
                    return unquoted;
                }
            } catch (Exception e2) {
                logger.warn("Failed to parse as string either, returning raw body");
            }
            return responseBody;
        } catch (Exception e) {
            logger.warn("Failed to parse LLM response, returning raw body", e);
            return responseBody;
        }
    }
}

