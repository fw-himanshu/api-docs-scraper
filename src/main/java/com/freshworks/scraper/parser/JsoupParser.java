package com.freshworks.scraper.parser;

import com.freshworks.scraper.model.Endpoint;
import com.freshworks.scraper.model.Example;
import com.freshworks.scraper.model.Parameter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jsoup-based HTML parser for API documentation.
 * Attempts to identify and extract endpoint information using common patterns.
 */
public class JsoupParser implements DocumentParser {
    private static final Logger logger = LoggerFactory.getLogger(JsoupParser.class);
    
    // HTTP method pattern
    private static final Pattern HTTP_METHOD_PATTERN = 
            Pattern.compile("\\b(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\b", Pattern.CASE_INSENSITIVE);
    
    // API path pattern (e.g., /api/users/{id}, /v1/events)
    private static final Pattern API_PATH_PATTERN = 
            Pattern.compile("(/[a-zA-Z0-9/_{}\\-:.]*)", Pattern.CASE_INSENSITIVE);

    @Override
    public List<Endpoint> parse(String html, String sourceUrl) {
        logger.info("Parsing HTML document from: {}", sourceUrl);
        
        Document doc = Jsoup.parse(html);
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Try multiple strategies to find endpoints
        endpoints.addAll(parseByMethodHeaders(doc));
        endpoints.addAll(parseByCodeBlocks(doc));
        endpoints.addAll(parseByTables(doc));
        
        logger.info("Extracted {} endpoints from: {}", endpoints.size(), sourceUrl);
        return endpoints;
    }
    
    /**
     * Looks for headings that contain HTTP methods (e.g., "GET /users")
     */
    private List<Endpoint> parseByMethodHeaders(Document doc) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Look for headings (h1-h6) that contain HTTP methods
        Elements headings = doc.select("h1, h2, h3, h4, h5, h6");
        
        for (Element heading : headings) {
            String text = heading.text();
            Matcher methodMatcher = HTTP_METHOD_PATTERN.matcher(text);
            Matcher pathMatcher = API_PATH_PATTERN.matcher(text);
            
            if (methodMatcher.find() && pathMatcher.find()) {
                String method = methodMatcher.group(1).toUpperCase();
                String path = pathMatcher.group(1);
                
                Endpoint endpoint = new Endpoint(method, path);
                endpoint.setSummary(text.trim());
                
                // Extract description from following content
                Element nextSibling = heading.nextElementSibling();
                if (nextSibling != null) {
                    String desc = extractDescription(nextSibling);
                    endpoint.setDescription(desc);
                    
                    // Look for parameters in following sections
                    extractParameters(heading.parent(), endpoint);
                    
                    // Look for code examples
                    extractExamples(heading.parent(), endpoint);
                }
                
                endpoints.add(endpoint);
                logger.debug("Found endpoint: {} {}", method, path);
            }
        }
        
        return endpoints;
    }
    
    /**
     * Looks for code blocks that might represent API calls
     */
    private List<Endpoint> parseByCodeBlocks(Document doc) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        Elements codeBlocks = doc.select("pre, code");
        
        for (Element code : codeBlocks) {
            String text = code.text();
            
            // Look for HTTP method patterns in code
            Matcher methodMatcher = HTTP_METHOD_PATTERN.matcher(text);
            if (methodMatcher.find()) {
                Matcher pathMatcher = API_PATH_PATTERN.matcher(text);
                if (pathMatcher.find()) {
                    String method = methodMatcher.group(1).toUpperCase();
                    String path = pathMatcher.group(1);
                    
                    // Avoid duplicates
                    boolean isDuplicate = endpoints.stream()
                            .anyMatch(e -> e.getMethod().equals(method) && e.getPath().equals(path));
                    
                    if (!isDuplicate) {
                        Endpoint endpoint = new Endpoint(method, path);
                        
                        // Try to find associated description
                        Element parent = code.parent();
                        if (parent != null) {
                            Element heading = findPreviousHeading(parent);
                            if (heading != null) {
                                endpoint.setSummary(heading.text());
                            }
                        }
                        
                        endpoints.add(endpoint);
                        logger.debug("Found endpoint in code block: {} {}", method, path);
                    }
                }
            }
        }
        
        return endpoints;
    }
    
    /**
     * Looks for tables that might contain parameter information
     */
    private List<Endpoint> parseByTables(Document doc) {
        // This method looks for standalone endpoint tables
        // Not yet implemented - returns empty list for now
        return new ArrayList<>();
    }
    
    private String extractDescription(Element element) {
        if (element.tagName().equals("p")) {
            return element.text();
        }
        
        // Look for description in nearby paragraphs
        Elements paragraphs = element.select("p");
        if (!paragraphs.isEmpty()) {
            return paragraphs.first().text();
        }
        
        return "";
    }
    
    private void extractParameters(Element section, Endpoint endpoint) {
        // Look for parameter tables
        Elements tables = section.select("table");
        
        for (Element table : tables) {
            // Check if this is a parameters table
            String tableText = table.text().toLowerCase();
            if (tableText.contains("parameter") || tableText.contains("field") || 
                tableText.contains("name") && tableText.contains("type")) {
                
                Elements rows = table.select("tr");
                boolean isHeader = true;
                
                for (Element row : rows) {
                    if (isHeader) {
                        isHeader = false;
                        continue; // Skip header row
                    }
                    
                    Elements cells = row.select("td, th");
                    if (cells.size() >= 2) {
                        Parameter param = new Parameter();
                        param.setName(cells.get(0).text().trim());
                        param.setType(cells.size() > 1 ? cells.get(1).text().trim() : "string");
                        param.setDescription(cells.size() > 2 ? cells.get(2).text().trim() : "");
                        
                        // Check if required
                        String rowText = row.text().toLowerCase();
                        param.setRequired(rowText.contains("required") || rowText.contains("yes"));
                        
                        endpoint.addParameter(param);
                    }
                }
            }
        }
    }
    
    private void extractExamples(Element section, Endpoint endpoint) {
        // Look for code blocks that contain JSON
        Elements codeBlocks = section.select("pre code, pre");
        
        for (Element code : codeBlocks) {
            String codeText = code.text().trim();
            
            // Check if it looks like JSON
            if (codeText.startsWith("{") || codeText.startsWith("[")) {
                // Determine if it's a request or response based on context
                String nearbyText = "";
                Element prev = code.previousElementSibling();
                if (prev != null) {
                    nearbyText = prev.text().toLowerCase();
                }
                
                Example example = new Example("json", codeText);
                
                if (nearbyText.contains("request") || nearbyText.contains("body")) {
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
    
    private Element findPreviousHeading(Element element) {
        Element current = element.previousElementSibling();
        
        while (current != null) {
            if (current.tagName().matches("h[1-6]")) {
                return current;
            }
            current = current.previousElementSibling();
        }
        
        // Try parent's previous heading
        if (element.parent() != null) {
            return findPreviousHeading(element.parent());
        }
        
        return null;
    }
}

