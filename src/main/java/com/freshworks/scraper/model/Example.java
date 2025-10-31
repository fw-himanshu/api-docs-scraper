package com.freshworks.scraper.model;

/**
 * Represents a code example (request or response).
 */
public class Example {
    private String language; // e.g., "json", "curl"
    private String code;
    private String description;

    public Example() {
    }

    public Example(String language, String code) {
        this.language = language;
        this.code = code;
    }

    public Example(String language, String code, String description) {
        this.language = language;
        this.code = code;
        this.description = description;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "Example{" +
                "language='" + language + '\'' +
                ", code length=" + (code != null ? code.length() : 0) +
                '}';
    }
}

