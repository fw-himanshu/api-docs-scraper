package com.freshworks.scraper.service;

import com.freshworks.scraper.ApiDocsScraper;
import com.freshworks.scraper.ScraperException;
import com.freshworks.scraper.exporter.JsonExporter;
import com.freshworks.scraper.model.Endpoint;
import com.freshworks.scraper.model.ScrapeJob;
import com.freshworks.scraper.model.ScrapedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service to manage asynchronous scraping jobs.
 */
@Service
public class JobService {
    
    private static final Logger logger = LoggerFactory.getLogger(JobService.class);
    
    private final Map<String, ScrapeJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    // Job retention time: 5 minutes
    private static final long JOB_RETENTION_MINUTES = 5;
    
    /**
     * Creates a new scraping job and queues it for processing.
     */
    public ScrapeJob createJob(String sourceUrl, boolean usePlaywright, String llmToken, 
                                List<String> additionalUrls) {
        String jobId = UUID.randomUUID().toString();
        ScrapeJob job = new ScrapeJob(jobId, sourceUrl);
        jobs.put(jobId, job);
        
        logger.info("Created scraping job {} for URL: {}", jobId, sourceUrl);
        
        // Process asynchronously
        executorService.submit(() -> processJob(jobId, sourceUrl, usePlaywright, llmToken, additionalUrls));
        
        return job;
    }
    
    /**
     * Gets a job by ID.
     */
    public ScrapeJob getJob(String jobId) {
        return jobs.get(jobId);
    }
    
    /**
     * Processes a scraping job asynchronously.
     */
    private void processJob(String jobId, String sourceUrl, boolean usePlaywright, 
                           String llmToken, List<String> additionalUrls) {
        ScrapeJob job = jobs.get(jobId);
        if (job == null) {
            logger.error("Job {} not found", jobId);
            return;
        }
        
        try {
            job.setStatus(ScrapeJob.Status.PROCESSING);
            job.setProgressMessage("Starting to scrape API documentation...");
            logger.info("Processing job {}: {}", jobId, sourceUrl);
            
            // Create scraper instance
            ApiDocsScraper scraper = new ApiDocsScraper(usePlaywright, llmToken);
            
            // Perform the scrape
            ScrapedResult result;
            if (additionalUrls != null && !additionalUrls.isEmpty()) {
                job.setProgressMessage("Scraping multiple URLs...");
                List<String> allUrls = java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(sourceUrl),
                    additionalUrls.stream()
                ).toList();
                result = scraper.scrapeMultiple(allUrls);
            } else {
                job.setProgressMessage("Fetching and parsing documentation...");
                result = scraper.scrape(sourceUrl);
            }
            
            List<Endpoint> endpoints = result.getEndpoints();
            job.setProgressMessage(String.format("Found %d endpoints, generating OpenAPI spec...", endpoints.size()));
            
            // Generate OpenAPI 3.0 specification
            String openApiSpec = null;
            try {
                logger.info("Generating OpenAPI 3.0 specification for job {}", jobId);
                String baseUrl = extractBaseUrl(sourceUrl);
                openApiSpec = scraper.generateOpenAPI(result, baseUrl);
                logger.info("OpenAPI specification generated successfully for job {}", jobId);
            } catch (ScraperException e) {
                logger.error("Failed to generate OpenAPI specification for job {}", jobId, e);
                // Continue with raw endpoint response
            }
            
            // Generate JSON output
            String jsonOutput = generateJsonOutput(result);
            
            // Update job with results
            job.setStatus(ScrapeJob.Status.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setEndpointCount(endpoints.size());
            job.setEndpoints(endpoints);
            job.setOpenApiSpec(openApiSpec);
            job.setJsonOutput(jsonOutput);
            job.setProgressMessage(String.format("Completed! Extracted %d endpoints", endpoints.size()));
            
            logger.info("Job {} completed successfully with {} endpoints", jobId, endpoints.size());
            
        } catch (Exception e) {
            logger.error("Job {} failed", jobId, e);
            job.setStatus(ScrapeJob.Status.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(e.getMessage());
            job.setProgressMessage("Scraping failed: " + e.getMessage());
        }
    }
    
    /**
     * Generates JSON output from the scraped result.
     */
    private String generateJsonOutput(ScrapedResult result) {
        try {
            Path tempFile = Files.createTempFile("scraper-output-" + UUID.randomUUID(), ".json");
            tempFile.toFile().deleteOnExit();
            
            JsonExporter exporter = new JsonExporter();
            exporter.export(result, tempFile);
            
            String json = Files.readString(tempFile);
            Files.deleteIfExists(tempFile);
            
            return json;
        } catch (IOException e) {
            logger.error("Failed to generate JSON output", e);
            return "{\"error\": \"Failed to generate JSON output: " + e.getMessage() + "\"}";
        }
    }
    
    /**
     * Extracts base URL from a full URL.
     */
    private String extractBaseUrl(String url) {
        try {
            java.net.URL urlObj = new java.net.URL(url);
            return String.format("%s://%s", urlObj.getProtocol(), urlObj.getHost());
        } catch (Exception e) {
            logger.warn("Could not extract base URL from: {}", url);
            return null;
        }
    }
    
    /**
     * Scheduled task to clean up old completed or failed jobs.
     * Runs every minute to check for jobs older than 5 minutes.
     */
    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    public void cleanupOldJobs() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(JOB_RETENTION_MINUTES);
        int initialSize = jobs.size();
        
        logger.debug("Starting job cleanup. Current jobs: {}", initialSize);
        
        // Collect job IDs to remove (safer for ConcurrentHashMap)
        List<String> jobsToRemove = jobs.entrySet().stream()
                .filter(entry -> {
                    ScrapeJob job = entry.getValue();
                    // Only clean up completed or failed jobs
                    if (job.getStatus() == ScrapeJob.Status.COMPLETED || 
                        job.getStatus() == ScrapeJob.Status.FAILED) {
                        LocalDateTime jobCompletionTime = job.getCompletedAt();
                        return jobCompletionTime != null && jobCompletionTime.isBefore(cutoffTime);
                    }
                    return false;
                })
                .map(Map.Entry::getKey)
                .toList();
        
        // Remove jobs
        int removedCount = 0;
        for (String jobId : jobsToRemove) {
            ScrapeJob removed = jobs.remove(jobId);
            if (removed != null) {
                removedCount++;
                logger.debug("Removed old {} job {} (completed at: {})", 
                        removed.getStatus(), jobId, removed.getCompletedAt());
            }
        }
        
        if (removedCount > 0) {
            logger.info("Cleaned up {} old job(s). Jobs remaining: {}", removedCount, jobs.size());
        }
    }
    
    /**
     * Gets statistics about current jobs (for monitoring/debugging).
     */
    public Map<String, Object> getJobStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalJobs", jobs.size());
        
        long queued = jobs.values().stream().filter(j -> j.getStatus() == ScrapeJob.Status.QUEUED).count();
        long processing = jobs.values().stream().filter(j -> j.getStatus() == ScrapeJob.Status.PROCESSING).count();
        long completed = jobs.values().stream().filter(j -> j.getStatus() == ScrapeJob.Status.COMPLETED).count();
        long failed = jobs.values().stream().filter(j -> j.getStatus() == ScrapeJob.Status.FAILED).count();
        
        stats.put("queued", queued);
        stats.put("processing", processing);
        stats.put("completed", completed);
        stats.put("failed", failed);
        
        return stats;
    }
}

