package com.freshworks.scraper.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an API endpoint with all its metadata.
 */
public class Endpoint {
    private String method;      // GET, POST, PUT, DELETE, etc.
    private String path;        // e.g., "/users/{id}"
    private String summary;     // Short title
    private String description; // Detailed description
    private List<Parameter> parameters;
    private Example requestExample;
    private Example responseExample;
    private List<String> tags;  // For categorization

    public Endpoint() {
        this.parameters = new ArrayList<>();
        this.tags = new ArrayList<>();
    }

    public Endpoint(String method, String path) {
        this();
        this.method = method;
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    public void addParameter(Parameter parameter) {
        this.parameters.add(parameter);
    }

    public Example getRequestExample() {
        return requestExample;
    }

    public void setRequestExample(Example requestExample) {
        this.requestExample = requestExample;
    }

    public Example getResponseExample() {
        return responseExample;
    }

    public void setResponseExample(Example responseExample) {
        this.responseExample = responseExample;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public void addTag(String tag) {
        this.tags.add(tag);
    }

    @Override
    public String toString() {
        return "Endpoint{" +
                "method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", summary='" + summary + '\'' +
                ", parameters=" + parameters.size() +
                '}';
    }
}

