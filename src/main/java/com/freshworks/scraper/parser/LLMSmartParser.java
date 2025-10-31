package com.freshworks.scraper.parser;

import com.freshworks.scraper.fetcher.FetchException;
import com.freshworks.scraper.fetcher.FetcherFactory;
import com.freshworks.scraper.fetcher.PageFetcher;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * LLM-powered parser for complex API documentation.
 * Uses LLM to discover all API endpoints from the page, then extracts each one.
 * Works with any documentation site, not limited to specific providers.
 */
public class LLMSmartParser implements DocumentParser {
    private static final Logger logger = LoggerFactory.getLogger(LLMSmartParser.class);
    
    private final LLMClient llmClient;
    private final boolean usePlaywright;
    private final Gson gson;
    
    public LLMSmartParser(LLMClient llmClient, boolean usePlaywright) {
        this.llmClient = llmClient;
        this.usePlaywright = usePlaywright;
        this.gson = new Gson();
    }
    
    @Override
    public List<Endpoint> parse(String html, String sourceUrl) {
        logger.info("Starting LLM Smart Parser on: {}", sourceUrl);
        
        List<Endpoint> allEndpoints = new ArrayList<>();
        
        try {
            //Step 1: Ask LLM to discover all API endpoints documented on this page
            List<ApiEndpointInfo> discoveredEndpoints = discoverAllEndpoints(html, sourceUrl);
            logger.info("LLM discovered {} API endpoints from the documentation", discoveredEndpoints.size());
            
            // Step 2: For each discovered endpoint, get its documentation URL and extract details
            logger.info("🔍 Step 2: Extracting detailed information for each endpoint in PARALLEL");
            logger.info("   📊 Total endpoints to process: {}", discoveredEndpoints.size());
            
            // Process endpoints in parallel
            allEndpoints = extractEndpointsInParallel(discoveredEndpoints, sourceUrl);
            
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
        } catch (Exception e) {
            logger.error("Error in LLM Smart Parser", e);
        }
        
        logger.info("LLM Smart Parser complete. Extracted {} endpoints", allEndpoints.size());
        return allEndpoints;
    }
    
    /**
     * Extracts endpoint details in parallel for better performance.
     */
    private List<Endpoint> extractEndpointsInParallel(List<ApiEndpointInfo> discoveredEndpoints, String sourceUrl) {
        List<Endpoint> allEndpoints = new ArrayList<>();
        int totalEndpoints = discoveredEndpoints.size();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);
        
        // Use ExecutorService with thread pool (max 5 concurrent LLM calls to avoid overwhelming the API)
        int poolSize = Math.min(5, totalEndpoints);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        
        logger.info("   🚀 Processing {} endpoints in parallel with {} threads", totalEndpoints, poolSize);
        long overallStartTime = System.currentTimeMillis();
        
        try {
            // Create CompletableFutures for each endpoint
            List<CompletableFuture<Void>> futures = discoveredEndpoints.stream()
                    .map(info -> CompletableFuture.runAsync(() -> {
                        int currentNum = processedCount.incrementAndGet();
                        long endpointStartTime = System.currentTimeMillis();
                        
                        try {
                            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                            logger.info("📋 Processing endpoint [{}/{}] in parallel", currentNum, totalEndpoints);
                            logger.info("   🔹 Method: {}", info.method);
                            logger.info("   🔹 Path: {}", info.path);
                            if (info.name != null) {
                                logger.info("   🔹 Name: {}", info.name);
                            }
                            if (info.url != null && !info.url.isEmpty()) {
                                logger.info("   🔹 Docs URL: {}", info.url);
                            }
                            
                            Endpoint endpoint = extractEndpointDetails(info, sourceUrl);
                            if (endpoint != null) {
                                synchronized (allEndpoints) {
                                    allEndpoints.add(endpoint);
                                }
                                
                                long endpointDuration = System.currentTimeMillis() - endpointStartTime;
                                logger.info("   ✅ Successfully extracted in {} ms", endpointDuration);
                                logger.info("      📌 Method: {}", endpoint.getMethod());
                                logger.info("      📌 Path: {}", endpoint.getPath());
                                logger.info("      📌 Summary: {}", endpoint.getSummary());
                                logger.info("      📌 Parameters: {}", endpoint.getParameters().size());
                                
                                if (endpoint.getDescription() != null && !endpoint.getDescription().isEmpty()) {
                                    logger.debug("      📄 Description: {}", 
                                            endpoint.getDescription().substring(0, Math.min(100, endpoint.getDescription().length())));
                                }
                                
                                successCount.incrementAndGet();
                            } else {
                                logger.warn("   ⚠️  Extracted endpoint was null");
                                failureCount.incrementAndGet();
                            }
                            
                        } catch (Exception e) {
                            long endpointDuration = System.currentTimeMillis() - endpointStartTime;
                            logger.error("   ❌ Failed to extract endpoint after {} ms", endpointDuration, e);
                            logger.error("      Method: {}, Path: {}", info.method, info.path);
                            failureCount.incrementAndGet();
                        }
                    }, executor))
                    .collect(Collectors.toList());
            
            // Wait for all futures to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            long overallDuration = System.currentTimeMillis() - overallStartTime;
            logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.info("✅ Parallel extraction complete in {} ms", overallDuration);
            logger.info("   ✅ Successful: {}", successCount.get());
            logger.info("   ❌ Failed: {}", failureCount.get());
            logger.info("   📊 Total: {}", totalEndpoints);
            
        } finally {
            // Shutdown executor
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        return allEndpoints;
    }
    
    /**
     * Uses LLM to discover all API endpoints mentioned in the documentation.
     */
    private List<ApiEndpointInfo> discoverAllEndpoints(String html, String sourceUrl) throws LLMException {
        logger.info("🔍 Step 1: Discovering all API endpoints from documentation");
        logger.info("   📄 Source URL: {}", sourceUrl);
        
        // Extract text content from HTML
        Document doc = Jsoup.parse(html);
        String textContent = doc.body().text();
        int originalLength = textContent.length();
        
        logger.info("   📊 Extracted text content: {} characters", originalLength);
        
        // Limit size for LLM
        if (textContent.length() > 10000) {
            textContent = textContent.substring(0, 10000) + "... (truncated)";
            logger.info("   ✂️  Truncated to 10,000 characters for LLM processing");
        }
        
        // Determine URL context
        String urlContext = determineUrlContext(sourceUrl);
        
        String systemPrompt = "You are an expert at analyzing API documentation. " +
                "You have access to the source URL and can use it to better understand the API structure and base URL. " +
                "Your task is to identify ALL API endpoints mentioned or documented on a page. " +
                "Return a JSON array of objects with this structure: " +
                "[{\"method\": \"GET\", \"path\": \"/users\", \"name\": \"Get User\", \"url\": \"full_url_if_available\"}]. " +
                "Include ALL endpoints you can find. Use the source URL to construct complete paths. " +
                "Return ONLY valid JSON.";
        
        String userPrompt = String.format(
                "Analyze this API documentation page and list ALL API endpoints you can find.\n\n" +
                "**Source URL:** %s\n" +
                "**URL Context:** %s\n\n" +
                "Use the source URL to understand the base URL and API structure. " +
                "If relative paths are mentioned in the content, combine them with the base URL from the source URL.\n\n" +
                "**Page Content:**\n%s\n\n" +
                "Return a JSON array of ALL API endpoints with their:\n" +
                "- HTTP method\n" +
                "- Complete path (infer from source URL if relative paths are mentioned)\n" +
                "- Name/description\n" +
                "- Full documentation URL if available",
                sourceUrl,
                urlContext,
                textContent
        );
        
        logger.info("   🤖 Calling LLM to discover endpoints...");
        String response = llmClient.call(systemPrompt, userPrompt);
        logger.info("   ✅ LLM discovery complete");
        
        List<ApiEndpointInfo> endpoints = parseEndpointDiscoveryResponse(response);
        logger.info("   📊 Discovery results: {} endpoints found", endpoints.size());
        
        return endpoints;
    }
    
    /**
     * Parses the LLM response containing discovered endpoints.
     */
    private List<ApiEndpointInfo> parseEndpointDiscoveryResponse(String response) {
        List<ApiEndpointInfo> endpoints = new ArrayList<>();
        
        try {
            // Clean up response
            String cleanResponse = cleanJsonResponse(response);
            logger.debug("Cleaned discovery response: {}", cleanResponse.substring(0, Math.min(500, cleanResponse.length())));
            
            JsonArray jsonArray = gson.fromJson(cleanResponse, JsonArray.class);
            
            for (JsonElement element : jsonArray) {
                if (element.isJsonObject()) {
                    JsonObject obj = element.getAsJsonObject();
                    
                    String method = getStringOrNull(obj, "method");
                    String path = getStringOrNull(obj, "path");
                    String name = getStringOrNull(obj, "name");
                    String url = getStringOrNull(obj, "url");
                    
                    if (method != null && path != null) {
                        endpoints.add(new ApiEndpointInfo(method, path, name, url));
                    }
                }
            }
            
            logger.info("Parsed {} endpoint entries from LLM response", endpoints.size());
            
        } catch (Exception e) {
            logger.error("Failed to parse endpoint discovery response: {}", response, e);
        }
        
        return endpoints;
    }
    
    /**
     * Extracts detailed information for a specific endpoint.
     */
    private Endpoint extractEndpointDetails(ApiEndpointInfo info, String baseUrl) {
        logger.debug("   🔍 Extracting details for: {} {}", info.method, info.path);
        
        // If we have a specific URL for this endpoint, fetch and parse it
        if (info.url != null && !info.url.isEmpty()) {
            logger.debug("   🌐 Fetching from dedicated URL: {}", info.url);
            return extractFromUrl(info);
        }
        
        logger.debug("   📝 Using basic endpoint creation (no dedicated URL available)");
        // Otherwise, use LLM to extract details from the already-loaded page
        return createBasicEndpoint(info);
    }
    
    private Endpoint extractFromUrl(ApiEndpointInfo info) {
        try {
            logger.debug("   📥 Fetching page content from: {}", info.url);
            PageFetcher fetcher = FetcherFactory.createFetcher(info.url, usePlaywright);
            long fetchStart = System.currentTimeMillis();
            String html = fetcher.fetch(info.url);
            long fetchDuration = System.currentTimeMillis() - fetchStart;
            fetcher.close();
            
            logger.debug("   ✅ Page fetched in {} ms ({} chars)", fetchDuration, html.length());
            
            // Extract full details using LLM
            logger.debug("   🤖 Sending to LLM for detailed extraction...");
            return extractWithLLM(html, info);
            
        } catch (FetchException e) {
            logger.warn("   ⚠️  Failed to fetch URL for {}: {}", info.path, e.getMessage());
            logger.warn("   🔄 Falling back to basic endpoint");
            return createBasicEndpoint(info);
        }
    }
    
    private Endpoint extractWithLLM(String html, ApiEndpointInfo info) {
        try {
            Document doc = Jsoup.parse(html);
            String textContent = doc.text();
            int originalLength = textContent.length();
            
            logger.debug("   📄 Parsed HTML: {} characters", originalLength);
            
            if (textContent.length() > 8000) {
                textContent = textContent.substring(0, 8000) + "... (truncated)";
                logger.debug("   ✂️  Truncated to 8,000 characters for LLM");
            }
            
            String systemPrompt = "You are an API documentation expert. Extract complete details about this specific API endpoint. " +
                    "Return a JSON object with: method, path, summary, description, and parameters array. " +
                    "For parameters: name, type, required (boolean), description. Return ONLY valid JSON.";
            
            String userPrompt = String.format(
                    "Extract complete details for the API endpoint: %s %s\n\n" +
                    "Page content:\n%s\n\n" +
                    "Return JSON with all available information about this endpoint.",
                    info.method, info.path, textContent
            );
            
            logger.debug("   🤖 Calling LLM for detailed extraction...");
            long llmStart = System.currentTimeMillis();
            String response = llmClient.call(systemPrompt, userPrompt);
            long llmDuration = System.currentTimeMillis() - llmStart;
            logger.debug("   ✅ LLM extraction completed in {} ms", llmDuration);
            
            logger.debug("   🔍 Parsing LLM response...");
            Endpoint result = parseEndpointDetailsResponse(response, info);
            logger.debug("   ✅ Endpoint details parsed successfully");
            
            return result;
            
        } catch (LLMException e) {
            logger.error("   ❌ LLM extraction failed: {}", e.getMessage());
            logger.warn("   🔄 Falling back to basic endpoint");
            return createBasicEndpoint(info);
        }
    }
    
    private Endpoint parseEndpointDetailsResponse(String response, ApiEndpointInfo info) {
        try {
            String cleanResponse = cleanJsonResponse(response);
            JsonObject json = gson.fromJson(cleanResponse, JsonObject.class);
            
            String method = getStringOrNull(json, "method");
            String path = getStringOrNull(json, "path");
            
            Endpoint endpoint = new Endpoint(
                    method != null ? method : info.method,
                    path != null ? path : info.path
            );
            
            endpoint.setSummary(getStringOrNull(json, "summary"));
            endpoint.setDescription(getStringOrNull(json, "description"));
            
            // Extract parameters
            if (json.has("parameters") && json.get("parameters").isJsonArray()) {
                JsonArray params = json.getAsJsonArray("parameters");
                for (JsonElement paramElement : params) {
                    if (paramElement.isJsonObject()) {
                        JsonObject paramObj = paramElement.getAsJsonObject();
                        String name = getStringOrNull(paramObj, "name");
                        String type = getStringOrNull(paramObj, "type");
                        boolean required = paramObj.has("required") && paramObj.get("required").getAsBoolean();
                        String description = getStringOrNull(paramObj, "description");
                        
                        if (name != null) {
                            Parameter param = new Parameter(name, type != null ? type : "string",
                                                          required, description);
                            endpoint.addParameter(param);
                        }
                    }
                }
            }
            
            return endpoint;
            
        } catch (Exception e) {
            logger.error("Failed to parse endpoint details: {}", e.getMessage());
            return createBasicEndpoint(info);
        }
    }
    
    private Endpoint createBasicEndpoint(ApiEndpointInfo info) {
        Endpoint endpoint = new Endpoint(info.method.toUpperCase(), info.path);
        endpoint.setSummary(info.name);
        return endpoint;
    }
    
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
    
    private String getStringOrNull(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }
    
    /**
     * Determines URL context information to help LLM understand the source better.
     */
    private String determineUrlContext(String sourceUrl) {
        String lowerUrl = sourceUrl.toLowerCase();
        StringBuilder context = new StringBuilder();
        
        // Detect platform
        if (lowerUrl.contains("github.com")) {
            context.append("GitHub - ");
        } else if (lowerUrl.contains("gitlab")) {
            context.append("GitLab - ");
        } else if (lowerUrl.contains("bitbucket")) {
            context.append("Bitbucket - ");
        }
        
        // Detect documentation framework
        if (lowerUrl.contains("stoplight")) {
            context.append("Stoplight Docs - ");
        } else if (lowerUrl.contains("redoc")) {
            context.append("Redoc - ");
        } else if (lowerUrl.contains("swagger")) {
            context.append("Swagger UI - ");
        } else if (lowerUrl.contains("docs.")) {
            context.append("Documentation Site - ");
        }
        
        // Extract base URL
        try {
            java.net.URL url = new java.net.URL(sourceUrl);
            String protocol = url.getProtocol();
            String host = url.getHost();
            String basePath = url.getPath();
            
            // Remove filename if present
            if (basePath.contains("/") && !basePath.endsWith("/")) {
                basePath = basePath.substring(0, basePath.lastIndexOf("/"));
            }
            
            context.append("Base: ").append(protocol).append("://").append(host);
            if (basePath != null && !basePath.isEmpty() && !basePath.equals("/")) {
                context.append(basePath);
            }
        } catch (Exception e) {
            context.append("URL: ").append(sourceUrl);
        }
        
        return context.toString();
    }
    
    /**
     * Internal class to hold discovered endpoint information.
     */
    private static class ApiEndpointInfo {
        String method;
        String path;
        String name;
        String url;
        
        ApiEndpointInfo(String method, String path, String name, String url) {
            this.method = method;
            this.path = path;
            this.name = name;
            this.url = url;
        }
    }
}


