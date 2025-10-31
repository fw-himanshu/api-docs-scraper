package com.freshworks.scraper.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents an asynchronous scraping job.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScrapeJob {
    
    public enum Status {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED
    }
    
    private String jobId;
    private Status status;
    private String sourceUrl;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private Integer endpointCount;
    private List<Endpoint> endpoints;
    private String openApiSpec;
    private String jsonOutput;
    private String progressMessage;
    
    public ScrapeJob() {
    }
    
    public ScrapeJob(String jobId, String sourceUrl) {
        this.jobId = jobId;
        this.sourceUrl = sourceUrl;
        this.status = Status.QUEUED;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getJobId() {
        return jobId;
    }
    
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public void setStatus(Status status) {
        this.status = status;
    }
    
    public String getSourceUrl() {
        return sourceUrl;
    }
    
    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Integer getEndpointCount() {
        return endpointCount;
    }
    
    public void setEndpointCount(Integer endpointCount) {
        this.endpointCount = endpointCount;
    }
    
    public List<Endpoint> getEndpoints() {
        return endpoints;
    }
    
    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }
    
    public String getOpenApiSpec() {
        return openApiSpec;
    }
    
    public void setOpenApiSpec(String openApiSpec) {
        this.openApiSpec = openApiSpec;
    }
    
    public String getJsonOutput() {
        return jsonOutput;
    }
    
    public void setJsonOutput(String jsonOutput) {
        this.jsonOutput = jsonOutput;
    }
    
    public String getProgressMessage() {
        return progressMessage;
    }
    
    public void setProgressMessage(String progressMessage) {
        this.progressMessage = progressMessage;
    }
}

