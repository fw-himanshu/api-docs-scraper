package com.freshworks.scraper.model;

/**
 * Represents a parameter (query, path, header, or body field) in an API endpoint.
 */
public class Parameter {
    private String name;
    private String type;
    private boolean required;
    private String description;
    private String location; // query, path, header, body

    public Parameter() {
    }

    public Parameter(String name, String type, boolean required, String description) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return "Parameter{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", required=" + required +
                ", location='" + location + '\'' +
                '}';
    }
}

