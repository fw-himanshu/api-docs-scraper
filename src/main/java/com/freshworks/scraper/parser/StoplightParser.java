package com.freshworks.scraper.parser;

import com.freshworks.scraper.model.Endpoint;
import com.freshworks.scraper.model.Example;
import com.freshworks.scraper.model.Parameter;
import com.freshworks.scraper.llm.LLMClient;
import com.freshworks.scraper.llm.LLMException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Specialized parser for Stoplight-based API documentation (like Calendly).
 * Extracts endpoints from navigation menus and detailed content sections.
 */
public class StoplightParser implements DocumentParser {
    private static final Logger logger = LoggerFactory.getLogger(StoplightParser.class);
    
    private static final Pattern HTTP_METHOD_PATTERN = 
            Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\b", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern API_PATH_PATTERN = 
            Pattern.compile("(/[a-zA-Z0-9/_{}\\-:.]+)", Pattern.CASE_INSENSITIVE);
    
    private final LLMClient llmClient;
    private final boolean useLLM;
    
    public StoplightParser() {
        this.llmClient = null;
        this.useLLM = false;
    }
    
    public StoplightParser(LLMClient llmClient) {
        this.llmClient = llmClient;
        this.useLLM = llmClient != null;
    }
    
    @Override
    public List<Endpoint> parse(String html, String sourceUrl) {
        logger.info("Parsing Stoplight documentation from: {}", sourceUrl);
        
        Document doc = Jsoup.parse(html);
        List<Endpoint> endpoints = new ArrayList<>();
        Set<String> processedEndpoints = new HashSet<>();
        
        // Strategy 1: Extract from navigation/menu structure
        endpoints.addAll(parseFromNavigation(doc, processedEndpoints));
        
        // Strategy 2: Parse operation/endpoint sections
        endpoints.addAll(parseOperationSections(doc, processedEndpoints));
        
        // Strategy 3: Parse from article/main content area
        endpoints.addAll(parseFromArticles(doc, processedEndpoints));
        
        // Strategy 4: Look for HTTP operations in any visible text
        endpoints.addAll(parseFallbackSearch(doc, processedEndpoints));
        
        logger.info("Extracted {} unique endpoints from: {}", endpoints.size(), sourceUrl);
        return endpoints;
    }
    
    /**
     * Extracts endpoints from navigation menu structure.
     * Stoplight docs typically have a sidebar with all API endpoints listed.
     */
    private List<Endpoint> parseFromNavigation(Document doc, Set<String> processed) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Common Stoplight navigation selectors
        String[] navSelectors = {
            "nav a", 
            ".sidebar a", 
            "[role='navigation'] a",
            ".sl-elements-api a",
            "[data-test-id*='nav'] a",
            "aside a"
        };
        
        for (String selector : navSelectors) {
            Elements links = doc.select(selector);
            logger.debug("Found {} navigation links with selector: {}", links.size(), selector);
            
            for (Element link : links) {
                String text = link.text().trim();
                String href = link.attr("href");
                
                Matcher methodMatcher = HTTP_METHOD_PATTERN.matcher(text);
                Matcher pathMatcher = API_PATH_PATTERN.matcher(text);
                
                if (methodMatcher.find() && pathMatcher.find()) {
                    String method = methodMatcher.group(1).toUpperCase();
                    String path = pathMatcher.group(1);
                    String key = method + " " + path;
                    
                    if (!processed.contains(key)) {
                        Endpoint endpoint = new Endpoint(method, path);
                        endpoint.setSummary(text);
                        
                        // Try to find detailed content for this endpoint
                        enrichEndpointFromLink(doc, href, endpoint);
                        
                        endpoints.add(endpoint);
                        processed.add(key);
                        logger.debug("Found endpoint in navigation: {} {}", method, path);
                    }
                }
            }
        }
        
        return endpoints;
    }
    
    /**
     * Parses operation sections (main content areas with endpoint details).
     */
    private List<Endpoint> parseOperationSections(Document doc, Set<String> processed) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Look for operation/endpoint sections
        String[] sectionSelectors = {
            "[data-test*='operation']",
            ".http-operation",
            "article[id*='operation']",
            ".sl-http-operation",
            "[class*='HttpOperation']"
        };
        
        for (String selector : sectionSelectors) {
            Elements sections = doc.select(selector);
            logger.debug("Found {} operation sections with selector: {}", sections.size(), selector);
            
            for (Element section : sections) {
                Endpoint endpoint = parseOperationSection(section);
                if (endpoint != null) {
                    String key = endpoint.getMethod() + " " + endpoint.getPath();
                    if (!processed.contains(key)) {
                        endpoints.add(endpoint);
                        processed.add(key);
                        logger.debug("Found endpoint in operation section: {} {}", 
                                endpoint.getMethod(), endpoint.getPath());
                    }
                }
            }
        }
        
        return endpoints;
    }
    
    /**
     * Parses endpoints from article/main content areas.
     */
    private List<Endpoint> parseFromArticles(Document doc, Set<String> processed) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        Elements articles = doc.select("article, main, .content, [role='main']");
        
        for (Element article : articles) {
            // Look for headings with HTTP methods
            Elements headings = article.select("h1, h2, h3, h4");
            
            for (Element heading : headings) {
                String text = heading.text();
                Matcher methodMatcher = HTTP_METHOD_PATTERN.matcher(text);
                Matcher pathMatcher = API_PATH_PATTERN.matcher(text);
                
                if (methodMatcher.find() && pathMatcher.find()) {
                    String method = methodMatcher.group(1).toUpperCase();
                    String path = pathMatcher.group(1);
                    String key = method + " " + path;
                    
                    if (!processed.contains(key)) {
                        Endpoint endpoint = new Endpoint(method, path);
                        endpoint.setSummary(text);
                        
                        // Extract detailed information from the article section
                        extractEndpointDetails(heading.parent(), endpoint);
                        
                        endpoints.add(endpoint);
                        processed.add(key);
                    }
                }
            }
        }
        
        return endpoints;
    }
    
    /**
     * Fallback search through all text content.
     */
    private List<Endpoint> parseFallbackSearch(Document doc, Set<String> processed) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Get all text and look for endpoint patterns
        String bodyText = doc.body().text();
        Matcher methodMatcher = HTTP_METHOD_PATTERN.matcher(bodyText);
        
        while (methodMatcher.find()) {
            String method = methodMatcher.group(1).toUpperCase();
            int start = methodMatcher.start();
            int end = Math.min(start + 200, bodyText.length());
            String context = bodyText.substring(start, end);
            
            Matcher pathMatcher = API_PATH_PATTERN.matcher(context);
            if (pathMatcher.find()) {
                String path = pathMatcher.group(1);
                String key = method + " " + path;
                
                if (!processed.contains(key) && path.length() > 3) { // Avoid false positives
                    Endpoint endpoint = new Endpoint(method, path);
                    endpoint.setSummary(method + " " + path);
                    
                    endpoints.add(endpoint);
                    processed.add(key);
                    logger.debug("Found endpoint in fallback search: {} {}", method, path);
                }
            }
        }
        
        return endpoints;
    }
    
    private Endpoint parseOperationSection(Element section) {
        // Extract method and path
        String sectionText = section.text();
        
        Matcher methodMatcher = HTTP_METHOD_PATTERN.matcher(sectionText);
        Matcher pathMatcher = API_PATH_PATTERN.matcher(sectionText);
        
        if (methodMatcher.find() && pathMatcher.find()) {
            String method = methodMatcher.group(1).toUpperCase();
            String path = pathMatcher.group(1);
            
            Endpoint endpoint = new Endpoint(method, path);
            
            // Extract details from the section
            extractEndpointDetails(section, endpoint);
            
            return endpoint;
        }
        
        return null;
    }
    
    private void extractEndpointDetails(Element section, Endpoint endpoint) {
        // Extract description
        Elements descriptions = section.select("p, .description, [class*='description']");
        if (!descriptions.isEmpty()) {
            endpoint.setDescription(descriptions.first().text());
        }
        
        // Extract parameters
        extractParameters(section, endpoint);
        
        // Extract examples
        extractExamples(section, endpoint);
        
        // Use LLM to enhance extraction if available
        if (useLLM && section.text().length() > 100) {
            enhanceWithLLM(section, endpoint);
        }
    }
    
    private void extractParameters(Element section, Endpoint endpoint) {
        // Look for parameter sections
        Elements paramSections = section.select(
                "table, .parameters, [class*='parameter'], [data-test*='param']");
        
        for (Element paramSection : paramSections) {
            String text = paramSection.text().toLowerCase();
            
            if (text.contains("parameter") || text.contains("field") || 
                    (text.contains("name") && text.contains("type"))) {
                
                Elements rows = paramSection.select("tr");
                for (Element row : rows) {
                    Elements cells = row.select("td, th");
                    if (cells.size() >= 2) {
                        String name = cells.get(0).text().trim();
                        
                        // Skip header rows
                        if (name.equalsIgnoreCase("name") || name.equalsIgnoreCase("parameter")) {
                            continue;
                        }
                        
                        Parameter param = new Parameter();
                        param.setName(name);
                        param.setType(cells.size() > 1 ? cells.get(1).text().trim() : "string");
                        param.setDescription(cells.size() > 2 ? cells.get(2).text().trim() : "");
                        
                        String rowText = row.text().toLowerCase();
                        param.setRequired(rowText.contains("required") || rowText.contains("yes"));
                        
                        endpoint.addParameter(param);
                    }
                }
            }
        }
    }
    
    private void extractExamples(Element section, Endpoint endpoint) {
        Elements codeBlocks = section.select("pre, code, [class*='code'], [class*='example']");
        
        for (Element code : codeBlocks) {
            String codeText = code.text().trim();
            
            if (codeText.startsWith("{") || codeText.startsWith("[")) {
                // Determine if it's request or response
                String nearbyText = "";
                Element prev = code.previousElementSibling();
                if (prev != null) {
                    nearbyText = prev.text().toLowerCase();
                }
                
                Example example = new Example("json", codeText);
                
                if (nearbyText.contains("request") || nearbyText.contains("body") || 
                        nearbyText.contains("payload")) {
                    if (endpoint.getRequestExample() == null) {
                        endpoint.setRequestExample(example);
                    }
                } else if (nearbyText.contains("response") || nearbyText.contains("example")) {
                    if (endpoint.getResponseExample() == null) {
                        endpoint.setResponseExample(example);
                    }
                }
            }
        }
    }
    
    private void enrichEndpointFromLink(Document doc, String href, Endpoint endpoint) {
        if (href == null || href.isEmpty()) {
            return;
        }
        
        // Try to find content related to this endpoint by href
        Element target = null;
        
        if (href.startsWith("#")) {
            String id = href.substring(1);
            target = doc.getElementById(id);
        }
        
        if (target != null) {
            extractEndpointDetails(target, endpoint);
        }
    }
    
    private void enhanceWithLLM(Element section, Endpoint endpoint) {
        if (llmClient == null) {
            return;
        }
        
        try {
            // Only use LLM for complex extraction tasks
            String htmlSnippet = section.html();
            
            // If description is missing, try to extract it
            if (endpoint.getDescription() == null || endpoint.getDescription().isEmpty()) {
                if (htmlSnippet.length() < 5000) { // Limit size
                    String description = llmClient.extract(
                            htmlSnippet,
                            "Extract a concise description of what this API endpoint does. " +
                            "Return only the description text, no additional formatting.");
                    
                    if (description != null && !description.trim().isEmpty()) {
                        endpoint.setDescription(description.trim());
                        logger.debug("Enhanced description using LLM for: {} {}", 
                                endpoint.getMethod(), endpoint.getPath());
                    }
                }
            }
            
        } catch (LLMException e) {
            logger.warn("Failed to enhance endpoint with LLM: {}", e.getMessage());
        }
    }
}


