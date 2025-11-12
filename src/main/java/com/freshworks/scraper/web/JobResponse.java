package com.freshworks.scraper.web;

import com.freshworks.scraper.model.ScrapeJob;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashMap;
import java.util.Map;

/**
 * Response DTO for job-related endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobResponse {
    
    private boolean success;
    private String message;
    private String jobId;
    private ScrapeJob.Status status;
    private String humorousMessage;
    private Map<String, Object> data;
    
    // Factory methods
    public static JobResponse queued(String jobId, String message) {
        JobResponse response = new JobResponse();
        response.success = true;
        response.jobId = jobId;
        response.status = ScrapeJob.Status.QUEUED;
        response.message = message;
        return response;
    }
    
    public static JobResponse pending(String jobId, String humorousMessage) {
        JobResponse response = new JobResponse();
        response.success = true;
        response.jobId = jobId;
        response.status = ScrapeJob.Status.PROCESSING;
        response.humorousMessage = humorousMessage;
        response.message = "Job is still processing";
        return response;
    }
    
    public static JobResponse completed(ScrapeJob job) {
        JobResponse response = new JobResponse();
        response.success = true;
        response.jobId = job.getJobId();
        response.status = ScrapeJob.Status.COMPLETED;
        response.message = "Job completed successfully";
        
        // Build data map with null-safe handling
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("endpoints", job.getEndpoints());
        dataMap.put("endpointCount", job.getEndpointCount());
        dataMap.put("sourceUrl", job.getSourceUrl());
        if (job.getOpenApiSpec() != null) {
            dataMap.put("openApiSpec", job.getOpenApiSpec());
        }
        if (job.getJsonOutput() != null) {
            dataMap.put("jsonOutput", job.getJsonOutput());
        }
        if (job.getJudgeScore() != null) {
            dataMap.put("judgeScore", job.getJudgeScore());
        }
        if (job.getJudgeIssues() != null && !job.getJudgeIssues().isEmpty()) {
            dataMap.put("judgeIssues", job.getJudgeIssues());
        }
        if (job.getRetryCount() != null && job.getRetryCount() > 0) {
            dataMap.put("retryCount", job.getRetryCount());
        }
        response.data = dataMap;
        
        return response;
    }
    
    public static JobResponse failed(String jobId, String errorMessage) {
        JobResponse response = new JobResponse();
        response.success = false;
        response.jobId = jobId;
        response.status = ScrapeJob.Status.FAILED;
        response.message = "Job failed";
        response.data = Map.of("error", errorMessage);
        return response;
    }
    
    public static JobResponse notFound(String jobId) {
        JobResponse response = new JobResponse();
        response.success = false;
        response.jobId = jobId;
        response.message = "Job not found";
        return response;
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getJobId() {
        return jobId;
    }
    
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
    
    public ScrapeJob.Status getStatus() {
        return status;
    }
    
    public void setStatus(ScrapeJob.Status status) {
        this.status = status;
    }
    
    public String getHumorousMessage() {
        return humorousMessage;
    }
    
    public void setHumorousMessage(String humorousMessage) {
        this.humorousMessage = humorousMessage;
    }
    
    public Map<String, Object> getData() {
        return data;
    }
    
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}

