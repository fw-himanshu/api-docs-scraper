package com.freshworks.scraper.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Application configuration loader.
 * Reads configuration from application.properties in resources.
 */
@Component
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final String PROPERTIES_FILE = "application.properties";
    
    private final Properties properties;
    
    // Spring Boot properties injection
    @Value("${llm.api.url:https://cloudverse.freshworkscorp.com/api/v2/chat}")
    private String llmApiUrl;
    
    @Value("${llm.api.model:anthropic-claude-4-sonnet}")
    private String llmModel;
    
    @Value("${llm.api.token:${LLM_API_TOKEN:}}")
    private String llmToken;
    
    @Value("${llm.api.temperature:0}")
    private double temperature;
    
    @Value("${llm.api.top_p:1}")
    private double topP;
    
    @Value("${llm.api.timeout.seconds:120}")
    private int timeoutSeconds;
    
    public AppConfig() {
        this.properties = new Properties();
        loadProperties();
    }
    
    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                logger.warn("Unable to find {}", PROPERTIES_FILE);
                return;
            }
            properties.load(input);
            logger.debug("Loaded configuration from {}", PROPERTIES_FILE);
        } catch (IOException e) {
            logger.error("Error loading configuration", e);
        }
    }
    
    /**
     * Gets the LLM API token from configuration.
     * First checks environment variable LLM_API_TOKEN, then system property,
     * then application.properties file.
     * 
     * @return LLM API token or null if not found
     */
    public String getLLMApiToken() {
        // Priority 1: Injected Spring property
        if (llmToken != null && !llmToken.isEmpty() && !llmToken.equals("YOUR_API_TOKEN_HERE")) {
            return llmToken;
        }
        
        // Priority 2: Environment variable
        String token = System.getenv("LLM_API_TOKEN");
        if (token != null && !token.isEmpty() && !token.equals("YOUR_API_TOKEN_HERE")) {
            return token;
        }
        
        // Priority 3: System property
        token = System.getProperty("llm.api.token");
        if (token != null && !token.isEmpty() && !token.equals("YOUR_API_TOKEN_HERE")) {
            return token;
        }
        
        // Priority 4: Properties file
        token = properties.getProperty("llm.api.token");
        if (token != null && !token.isEmpty() && !token.equals("YOUR_API_TOKEN_HERE")) {
            return token;
        }
        
        return null;
    }
    
    /**
     * Gets the LLM API URL.
     * 
     * @return LLM API URL
     */
    public String getLLMApiUrl() {
        if (llmApiUrl != null && !llmApiUrl.isEmpty()) {
            return llmApiUrl;
        }
        return properties.getProperty("llm.api.url", 
                "https://cloudverse.freshworkscorp.com/api/v2/chat");
    }
    
    /**
     * Gets the LLM model name.
     * 
     * @return LLM model name
     */
    public String getLLMApiModel() {
        if (llmModel != null && !llmModel.isEmpty()) {
            return llmModel;
        }
        return properties.getProperty("llm.api.model", "anthropic-claude-4-sonnet");
    }
    
    /**
     * Gets the LLM API temperature setting.
     * 
     * @return Temperature value
     */
    public double getLLMApiTemperature() {
        if (temperature > 0 || (temperature == 0 && llmModel != null)) {
            return temperature;
        }
        return Double.parseDouble(properties.getProperty("llm.api.temperature", "0"));
    }
    
    /**
     * Gets the LLM API top_p setting.
     * 
     * @return top_p value
     */
    public double getLLMApiTopP() {
        if (topP > 0 || (topP == 1.0 && llmModel != null)) {
            return topP;
        }
        return Double.parseDouble(properties.getProperty("llm.api.top_p", "1"));
    }
    
    /**
     * Gets the LLM API timeout in seconds.
     * 
     * @return Timeout in seconds
     */
    public int getLLMApiTimeoutSeconds() {
        if (timeoutSeconds > 0) {
            return timeoutSeconds;
        }
        return Integer.parseInt(properties.getProperty("llm.api.timeout.seconds", "120"));
    }
    
    /**
     * Gets a property value by key.
     * 
     * @param key Property key
     * @return Property value or null if not found
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * Gets a property value with a default.
     * 
     * @param key Property key
     * @param defaultValue Default value if not found
     * @return Property value or default
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}

