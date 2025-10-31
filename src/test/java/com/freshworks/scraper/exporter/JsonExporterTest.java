package com.freshworks.scraper.exporter;

import com.freshworks.scraper.model.Endpoint;
import com.freshworks.scraper.model.Parameter;
import com.freshworks.scraper.model.ScrapedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JsonExporterTest {

    private JsonExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new JsonExporter();
    }

    @Test
    void testToJson() {
        ScrapedResult result = new ScrapedResult("http://example.com");
        
        Endpoint endpoint = new Endpoint("GET", "/api/test");
        endpoint.setSummary("Test endpoint");
        endpoint.setDescription("This is a test endpoint");
        
        Parameter param = new Parameter("id", "integer", true, "The resource ID");
        endpoint.addParameter(param);
        
        result.addEndpoint(endpoint);
        
        String json = exporter.toJson(result);
        
        assertNotNull(json);
        assertTrue(json.contains("\"method\": \"GET\""));
        assertTrue(json.contains("\"/api/test\""));
        assertTrue(json.contains("\"source\": \"http://example.com\""));
        
        System.out.println("Generated JSON:");
        System.out.println(json);
    }

    @Test
    void testExport(@TempDir Path tempDir) throws Exception {
        ScrapedResult result = new ScrapedResult("http://example.com");
        
        Endpoint endpoint1 = new Endpoint("GET", "/api/users");
        endpoint1.setSummary("Get users");
        result.addEndpoint(endpoint1);
        
        Endpoint endpoint2 = new Endpoint("POST", "/api/users");
        endpoint2.setSummary("Create user");
        result.addEndpoint(endpoint2);
        
        Path outputFile = tempDir.resolve("test-output.json");
        exporter.export(result, outputFile);
        
        assertTrue(Files.exists(outputFile), "Output file should be created");
        
        String content = Files.readString(outputFile);
        assertFalse(content.isEmpty(), "Output file should not be empty");
        assertTrue(content.contains("GET"));
        assertTrue(content.contains("POST"));
        
        System.out.println("Exported to: " + outputFile);
        System.out.println("Content length: " + content.length() + " bytes");
    }
}

