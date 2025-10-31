package com.freshworks.scraper.exporter;

import com.freshworks.scraper.model.ScrapedResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Exports scraped data to JSON format.
 */
public class JsonExporter {
    private static final Logger logger = LoggerFactory.getLogger(JsonExporter.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final Gson gson;

    public JsonExporter() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .registerTypeAdapter(LocalDateTime.class, 
                    (JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) -> 
                        new JsonPrimitive(src.format(DATE_TIME_FORMATTER)))
                .registerTypeAdapter(LocalDateTime.class,
                    (JsonDeserializer<LocalDateTime>) (json, typeOfT, context) ->
                        LocalDateTime.parse(json.getAsString(), DATE_TIME_FORMATTER))
                .create();
    }

    /**
     * Exports scraped result to a JSON file.
     * 
     * @param result The scraped result to export
     * @param outputPath The output file path
     * @throws IOException if writing fails
     */
    public void export(ScrapedResult result, Path outputPath) throws IOException {
        logger.info("Exporting {} endpoints to: {}", result.getEndpoints().size(), outputPath);
        
        try (FileWriter writer = new FileWriter(outputPath.toFile())) {
            gson.toJson(result, writer);
            logger.info("Successfully exported to: {}", outputPath);
        } catch (IOException e) {
            logger.error("Failed to export to: {}", outputPath, e);
            throw e;
        }
    }

    /**
     * Converts scraped result to JSON string.
     * 
     * @param result The scraped result to convert
     * @return JSON string representation
     */
    public String toJson(ScrapedResult result) {
        return gson.toJson(result);
    }
}

