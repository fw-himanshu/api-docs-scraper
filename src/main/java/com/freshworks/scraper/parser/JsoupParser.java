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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        Set<String> processedEndpoints = new HashSet<>();
        
        // Try multiple strategies to find endpoints
        endpoints.addAll(parseFromNavigation(doc, processedEndpoints));
        endpoints.addAll(parseByMethodHeaders(doc, processedEndpoints));
        endpoints.addAll(parseByHttpRequestSections(doc, processedEndpoints));
        endpoints.addAll(parseByCodeBlocks(doc, processedEndpoints));
        endpoints.addAll(parseByTables(doc, processedEndpoints));
        
        logger.info("Extracted {} endpoints from: {}", endpoints.size(), sourceUrl);
        return endpoints;
    }
    
    /**
     * Extracts endpoints from navigation menus (common in Slate docs).
     */
    private List<Endpoint> parseFromNavigation(Document doc, Set<String> processed) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Common navigation selectors for Slate and other doc frameworks
        String[] navSelectors = {
            "nav a",
            ".sidebar a",
            ".toc a",
            "[role='navigation'] a",
            ".tocify a",
            ".slate-nav a",
            "aside a",
            ".nav-group a"
        };
        
        for (String selector : navSelectors) {
            Elements links = doc.select(selector);
            logger.debug("Found {} navigation links with selector: {}", links.size(), selector);
            
            for (Element link : links) {
                String text = link.text().trim();
                String href = link.attr("href");
                
                // Check if link text contains HTTP method and path
                Matcher methodMatcher = HTTP_METHOD_PATTERN.matcher(text);
                Matcher pathMatcher = API_PATH_PATTERN.matcher(text);
                
                if (methodMatcher.find() && pathMatcher.find()) {
                    String method = methodMatcher.group(1).toUpperCase();
                    String path = pathMatcher.group(1);
                    
                    if (path != null && !path.isEmpty()) {
                        String key = method + " " + path;
                        if (!processed.contains(key)) {
                            Endpoint endpoint = new Endpoint(method, path);
                            endpoint.setSummary(text);
                            endpoints.add(endpoint);
                            processed.add(key);
                            logger.debug("Found endpoint in navigation: {} {}", method, path);
                        }
                    }
                } else {
                    // Check href for API paths even if text doesn't show method
                    pathMatcher = API_PATH_PATTERN.matcher(href);
                    if (pathMatcher.find()) {
                        String path = pathMatcher.group(1);
                        // Try to infer method from context or use GET as default
                        String method = "GET";
                        Matcher hrefMethodMatcher = HTTP_METHOD_PATTERN.matcher(text.toLowerCase());
                        if (hrefMethodMatcher.find()) {
                            method = hrefMethodMatcher.group(1).toUpperCase();
                        }
                        
                        String key = method + " " + path;
                        if (!processed.contains(key)) {
                            Endpoint endpoint = new Endpoint(method, path);
                            endpoint.setSummary(text.isEmpty() ? path : text);
                            endpoints.add(endpoint);
                            processed.add(key);
                            logger.debug("Found endpoint in navigation href: {} {}", method, path);
                        }
                    }
                }
            }
        }
        
        return endpoints;
    }
    
    /**
     * Extracts path from href attribute.
     */
    private String extractPathFromHref(String href) {
        if (href == null || href.isEmpty()) {
            return null;
        }
        
        try {
            // Remove anchor fragments
            int anchorIndex = href.indexOf('#');
            if (anchorIndex >= 0) {
                href = href.substring(0, anchorIndex);
            }
            
            // Extract path from URL
            java.net.URL url = new java.net.URL(href.startsWith("http") ? href : "http://example.com" + href);
            String path = url.getPath();
            
            // Check if path looks like an API endpoint
            if (path.contains("/wp-json/") || path.contains("/api/") || path.contains("/v1/") || 
                path.contains("/v2/") || path.contains("/v3/")) {
                return path;
            }
            
            return null;
        } catch (Exception e) {
            // Try simple pattern matching
            Matcher pathMatcher = API_PATH_PATTERN.matcher(href);
            if (pathMatcher.find()) {
                return pathMatcher.group(1);
            }
            return null;
        }
    }
    
    /**
     * Parses "HTTP request" sections common in Slate documentation.
     */
    private List<Endpoint> parseByHttpRequestSections(Document doc, Set<String> processed) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Look for headings containing "HTTP request" or similar
        Elements headings = doc.select("h1, h2, h3, h4, h5, h6");
        
        for (Element heading : headings) {
            String headingText = heading.text().toLowerCase();
            if (headingText.contains("http request") || headingText.contains("request")) {
                // Look for method and path in the next elements
                Element current = heading.nextElementSibling();
                int attempts = 0;
                
                while (current != null && attempts < 5) {
                    String text = current.text();
                    
                    Matcher methodMatcher = HTTP_METHOD_PATTERN.matcher(text);
                    Matcher pathMatcher = API_PATH_PATTERN.matcher(text);
                    
                    if (methodMatcher.find() && pathMatcher.find()) {
                        String method = methodMatcher.group(1).toUpperCase();
                        String path = pathMatcher.group(1);
                        String key = method + " " + path;
                        
                        if (!processed.contains(key)) {
                            Endpoint endpoint = new Endpoint(method, path);
                            endpoint.setSummary(heading.text());
                            
                            // Extract description from following content
                            extractDescriptionAndParams(current, endpoint);
                            
                            endpoints.add(endpoint);
                            processed.add(key);
                            logger.debug("Found endpoint in HTTP request section: {} {}", method, path);
                            break; // Found endpoint, move to next heading
                        }
                    }
                    
                    current = current.nextElementSibling();
                    attempts++;
                }
            }
        }
        
        return endpoints;
    }
    
    private void extractDescriptionAndParams(Element section, Endpoint endpoint) {
        // Look for description paragraphs
        Elements paragraphs = section.parent().select("p");
        if (!paragraphs.isEmpty()) {
            StringBuilder desc = new StringBuilder();
            for (Element p : paragraphs) {
                String text = p.text().trim();
                if (!text.isEmpty() && !text.matches("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS).*")) {
                    if (desc.length() > 0) desc.append(" ");
                    desc.append(text);
                }
            }
            if (desc.length() > 0) {
                endpoint.setDescription(desc.toString());
            }
        }
        
        // Extract parameters from tables
        extractParameters(section.parent(), endpoint);
    }
    
    /**
     * Looks for headings that contain HTTP methods (e.g., "GET /users")
     */
    private List<Endpoint> parseByMethodHeaders(Document doc, Set<String> processed) {
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
                String key = method + " " + path;
                
                if (!processed.contains(key)) {
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
                    processed.add(key);
                    logger.debug("Found endpoint: {} {}", method, path);
                }
            }
        }
        
        return endpoints;
    }
    
    /**
     * Looks for code blocks that might represent API calls
     */
    private List<Endpoint> parseByCodeBlocks(Document doc, Set<String> processed) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        Elements codeBlocks = doc.select("pre code, pre, code");
        
        for (Element code : codeBlocks) {
            String text = code.text();
            
            // Look for HTTP method patterns in code
            Matcher methodMatcher = HTTP_METHOD_PATTERN.matcher(text);
            if (methodMatcher.find()) {
                Matcher pathMatcher = API_PATH_PATTERN.matcher(text);
                if (pathMatcher.find()) {
                    String method = methodMatcher.group(1).toUpperCase();
                    String path = pathMatcher.group(1);
                    String key = method + " " + path;
                    
                    if (!processed.contains(key)) {
                        Endpoint endpoint = new Endpoint(method, path);
                        
                        // Try to find associated description
                        Element parent = code.parent();
                        if (parent != null) {
                            Element heading = findPreviousHeading(parent);
                            if (heading != null) {
                                endpoint.setSummary(heading.text());
                            }
                        }
                        
                        // Extract from curl commands or similar
                        if (text.contains("curl")) {
                            // Extract path from curl command
                            Matcher curlPathMatcher = Pattern.compile("curl\\s+[^\\s]+\\s+([^\\s]+)").matcher(text);
                            if (curlPathMatcher.find()) {
                                String curlPath = curlPathMatcher.group(1);
                                if (curlPath.matches("/.*")) {
                                    path = curlPath;
                                    endpoint.setPath(path);
                                    key = method + " " + path;
                                }
                            }
                        }
                        
                        endpoints.add(endpoint);
                        processed.add(key);
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
    private List<Endpoint> parseByTables(Document doc, Set<String> processed) {
        List<Endpoint> endpoints = new ArrayList<>();
        
        // Look for tables that might list endpoints
        Elements tables = doc.select("table");
        
        for (Element table : tables) {
            Elements rows = table.select("tr");
            for (Element row : rows) {
                String rowText = row.text();
                Matcher methodMatcher = HTTP_METHOD_PATTERN.matcher(rowText);
                Matcher pathMatcher = API_PATH_PATTERN.matcher(rowText);
                
                if (methodMatcher.find() && pathMatcher.find()) {
                    String method = methodMatcher.group(1).toUpperCase();
                    String path = pathMatcher.group(1);
                    String key = method + " " + path;
                    
                    if (!processed.contains(key)) {
                        Endpoint endpoint = new Endpoint(method, path);
                        endpoint.setSummary(rowText.trim());
                        endpoints.add(endpoint);
                        processed.add(key);
                        logger.debug("Found endpoint in table: {} {}", method, path);
                    }
                }
            }
        }
        
        return endpoints;
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

