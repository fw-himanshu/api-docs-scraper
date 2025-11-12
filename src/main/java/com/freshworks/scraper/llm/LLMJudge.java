package com.freshworks.scraper.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM-powered judge to evaluate the quality of generated OpenAPI specifications.
 * Determines if the spec is complete, accurate, and properly formatted.
 */
public class LLMJudge {
    private static final Logger logger = LoggerFactory.getLogger(LLMJudge.class);
    
    private final LLMClient llmClient;
    private final Gson gson;
    
    public LLMJudge(LLMClient llmClient) {
        this.llmClient = llmClient;
        this.gson = new Gson();
    }
    
    /**
     * Evaluates the quality of a generated OpenAPI specification.
     * 
     * @param openApiSpec The OpenAPI specification JSON string
     * @param expectedEndpointCount The expected number of endpoints
     * @param sourceUrl The source URL of the documentation
     * @return JudgeResult containing quality score and feedback
     * @throws LLMException if evaluation fails
     */
    public JudgeResult evaluateOpenAPISpec(String openApiSpec, int expectedEndpointCount, String sourceUrl) throws LLMException {
        logger.info("⚖️  LLM Judge: Evaluating OpenAPI specification quality...");
        logger.info("   📊 Expected endpoints: {}", expectedEndpointCount);
        
        // Limit spec size for LLM evaluation (first 5000 chars should be enough)
        String specPreview = openApiSpec.length() > 5000 
            ? openApiSpec.substring(0, 5000) + "... (truncated for evaluation)"
            : openApiSpec;
        
        String systemPrompt = "You are an expert OpenAPI 3.0 specification evaluator. " +
                "Your task is to evaluate the quality and completeness of an OpenAPI specification. " +
                "Return a JSON object with: {\"score\": 0-100, \"isValid\": true/false, \"issues\": [\"issue1\", \"issue2\"], \"recommendation\": \"retry\" or \"accept\"}. " +
                "Score criteria:\n" +
                "- 90-100: Excellent - Complete, accurate, well-structured\n" +
                "- 70-89: Good - Minor issues, mostly complete\n" +
                "- 50-69: Fair - Some missing endpoints or incomplete details\n" +
                "- 0-49: Poor - Major issues, incomplete, or invalid\n" +
                "Return ONLY valid JSON, no markdown, no code blocks.";
        
        String userPrompt = String.format(
                "Evaluate this OpenAPI 3.0 specification:\n\n" +
                "**Source URL:** %s\n" +
                "**Expected Endpoints:** %d\n\n" +
                "**OpenAPI Spec (preview):**\n%s\n\n" +
                "Check for:\n" +
                "1. Completeness: Are all expected endpoints present?\n" +
                "2. Validity: Is the JSON valid and properly structured?\n" +
                "3. Completeness: Do endpoints have proper descriptions, parameters, and responses?\n" +
                "4. Server URL: Is the server URL correctly extracted from documentation?\n" +
                "5. Schema definitions: Are request/response schemas properly defined?\n\n" +
                "Return JSON with score, isValid, issues array, and recommendation.",
                sourceUrl,
                expectedEndpointCount,
                specPreview
        );
        
        try {
            String response = llmClient.call(systemPrompt, userPrompt, 30); // 30 second timeout for evaluation
            return parseJudgeResponse(response, openApiSpec, expectedEndpointCount);
        } catch (LLMException e) {
            logger.warn("LLM Judge evaluation failed, performing basic validation", e);
            // Fallback to basic validation
            return performBasicValidation(openApiSpec, expectedEndpointCount);
        }
    }
    
    /**
     * Parses the LLM judge response into a JudgeResult.
     */
    private JudgeResult parseJudgeResponse(String response, String openApiSpec, int expectedEndpointCount) {
        try {
            String cleanResponse = cleanJsonResponse(response);
            JsonObject judgeJson = gson.fromJson(cleanResponse, JsonObject.class);
            
            int score = judgeJson.has("score") ? judgeJson.get("score").getAsInt() : 50;
            boolean isValid = judgeJson.has("isValid") ? judgeJson.get("isValid").getAsBoolean() : false;
            String recommendation = judgeJson.has("recommendation") 
                ? judgeJson.get("recommendation").getAsString() 
                : (score >= 70 ? "accept" : "retry");
            
            java.util.List<String> issues = new java.util.ArrayList<>();
            if (judgeJson.has("issues") && judgeJson.get("issues").isJsonArray()) {
                for (var issue : judgeJson.getAsJsonArray("issues")) {
                    issues.add(issue.getAsString());
                }
            }
            
            // Also perform basic validation
            BasicValidationResult basicValidation = performBasicChecks(openApiSpec, expectedEndpointCount);
            
            // Combine LLM evaluation with basic checks
            if (!basicValidation.isValidJson()) {
                score = Math.min(score, 30); // Cap score if JSON is invalid
                isValid = false;
                issues.add("Invalid JSON structure");
            }
            
            if (basicValidation.getActualPathCount() < expectedEndpointCount * 0.8) {
                score = Math.min(score, 50); // Cap score if too many endpoints missing
                issues.add(String.format("Missing endpoints: Expected %d, found %d", 
                    expectedEndpointCount, basicValidation.getActualPathCount()));
            }
            
            JudgeResult result = new JudgeResult(score, isValid, issues, recommendation);
            logger.info("   ⚖️  Judge Score: {}/100", score);
            logger.info("   ✅ Valid: {}", isValid);
            logger.info("   📋 Recommendation: {}", recommendation);
            if (!issues.isEmpty()) {
                logger.info("   ⚠️  Issues found: {}", issues.size());
                issues.forEach(issue -> logger.info("      - {}", issue));
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to parse judge response", e);
            return performBasicValidation(openApiSpec, expectedEndpointCount);
        }
    }
    
    /**
     * Performs basic validation when LLM evaluation fails.
     */
    private JudgeResult performBasicValidation(String openApiSpec, int expectedEndpointCount) {
        BasicValidationResult basic = performBasicChecks(openApiSpec, expectedEndpointCount);
        
        int score = 50; // Default score
        boolean isValid = basic.isValidJson();
        java.util.List<String> issues = new java.util.ArrayList<>();
        String recommendation = "accept";
        
        if (!basic.isValidJson()) {
            score = 20;
            isValid = false;
            issues.add("Invalid JSON structure");
            recommendation = "retry";
        } else if (basic.getActualPathCount() < expectedEndpointCount * 0.8) {
            score = 40;
            issues.add(String.format("Missing endpoints: Expected %d, found %d", 
                expectedEndpointCount, basic.getActualPathCount()));
            recommendation = "retry";
        } else if (basic.getActualPathCount() == expectedEndpointCount) {
            score = 80;
        }
        
        return new JudgeResult(score, isValid, issues, recommendation);
    }
    
    /**
     * Performs basic structural checks on the OpenAPI spec.
     */
    private BasicValidationResult performBasicChecks(String openApiSpec, int expectedEndpointCount) {
        try {
            JsonObject spec = gson.fromJson(openApiSpec, JsonObject.class);
            
            // Check for required OpenAPI fields
            boolean hasOpenApi = spec.has("openapi");
            boolean hasInfo = spec.has("info");
            boolean hasPaths = spec.has("paths");
            boolean hasServers = spec.has("servers");
            
            int pathCount = hasPaths ? spec.getAsJsonObject("paths").size() : 0;
            
            boolean isValid = hasOpenApi && hasInfo && hasPaths;
            
            return new BasicValidationResult(isValid, pathCount, hasServers);
            
        } catch (Exception e) {
            return new BasicValidationResult(false, 0, false);
        }
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
     * Result of LLM judge evaluation.
     */
    public static class JudgeResult {
        private final int score;
        private final boolean isValid;
        private final java.util.List<String> issues;
        private final String recommendation;
        
        public JudgeResult(int score, boolean isValid, java.util.List<String> issues, String recommendation) {
            this.score = score;
            this.isValid = isValid;
            this.issues = issues;
            this.recommendation = recommendation;
        }
        
        public int getScore() {
            return score;
        }
        
        public boolean isValid() {
            return isValid;
        }
        
        public java.util.List<String> getIssues() {
            return issues;
        }
        
        public String getRecommendation() {
            return recommendation;
        }
        
        public boolean shouldRetry() {
            return "retry".equalsIgnoreCase(recommendation) || score < 70 || !isValid;
        }
    }
    
    /**
     * Basic validation result.
     */
    private static class BasicValidationResult {
        private final boolean validJson;
        private final int actualPathCount;
        private final boolean hasServers;
        
        public BasicValidationResult(boolean validJson, int actualPathCount, boolean hasServers) {
            this.validJson = validJson;
            this.actualPathCount = actualPathCount;
            this.hasServers = hasServers;
        }
        
        public boolean isValidJson() {
            return validJson;
        }
        
        public int getActualPathCount() {
            return actualPathCount;
        }
        
        public boolean hasServers() {
            return hasServers;
        }
    }
}

