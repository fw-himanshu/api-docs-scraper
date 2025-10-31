package com.freshworks.scraper.cli;

import com.freshworks.scraper.ApiDocsScraper;
import com.freshworks.scraper.ScraperException;
import com.freshworks.scraper.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Command-line interface for the API documentation scraper.
 */
@Command(
    name = "api-docs-scraper",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Scrapes API documentation and exports structured endpoint data to JSON."
)
public class ScraperCLI implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(ScraperCLI.class);

    @Option(
        names = {"-u", "--url"},
        description = "URL(s) to scrape (can specify multiple times)",
        required = false
    )
    private List<String> urls;

    @Parameters(
        index = "0..*",
        description = "URL(s) to scrape (alternative to --url)",
        arity = "0..*"
    )
    private List<String> positionalUrls;

    @Option(
        names = {"-o", "--output"},
        description = "Output file path (default: scraped-endpoints.json)",
        defaultValue = "scraped-endpoints.json"
    )
    private String outputPath;

    @Option(
        names = {"-r", "--render"},
        description = "Force Playwright rendering for JavaScript-heavy pages",
        defaultValue = "false"
    )
    private boolean usePlaywright;

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose (debug) logging",
        defaultValue = "false"
    )
    private boolean verbose;

    @Option(
        names = {"-c", "--config"},
        description = "Path to configuration file (YAML/JSON)"
    )
    private String configPath;

    @Option(
        names = {"-t", "--llm-token"},
        description = "LLM API token for enhanced parsing (optional)"
    )
    private String llmToken;

    @Option(
        names = {"--llm-token-env"},
        description = "Environment variable name for LLM token (default: LLM_API_TOKEN)",
        defaultValue = "LLM_API_TOKEN"
    )
    private String llmTokenEnv;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ScraperCLI()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        // Set logging level
        if (verbose) {
            setLogLevel("DEBUG");
        }

        // Combine URLs from both options and positional parameters
        List<String> allUrls = combineUrls();

        if (allUrls.isEmpty()) {
            logger.error("No URLs provided. Use --url or provide URLs as arguments.");
            System.err.println("Error: No URLs provided.");
            System.err.println("Usage: api-docs-scraper --url <url> [--output <file>] [--render]");
            System.err.println("   or: api-docs-scraper <url> [<url>...] [--output <file>] [--render]");
            return 1;
        }

        try {
            logger.info("API Docs Scraper started");
            logger.info("URLs to scrape: {}", allUrls);
            logger.info("Output file: {}", outputPath);
            logger.info("Use Playwright: {}", usePlaywright);

            // Get LLM token from multiple sources with priority:
            // 1. Command line option (-t/--llm-token)
            // 2. Environment variable (LLM_API_TOKEN by default)
            // 3. application.properties file
            AppConfig config = new AppConfig();
            String token = llmToken;
            
            if (token == null || token.isEmpty()) {
                token = System.getenv(llmTokenEnv);
                logger.debug("Checked environment variable {}: {}", llmTokenEnv, 
                        token != null ? "found" : "not found");
            }
            
            if (token == null || token.isEmpty()) {
                token = config.getLLMApiToken();
                if (token != null) {
                    logger.debug("Using LLM token from application.properties");
                }
            }
            
            if (token != null && !token.isEmpty()) {
                logger.info("LLM integration enabled for enhanced parsing");
            } else {
                logger.info("LLM integration disabled (no token provided)");
            }

            ApiDocsScraper scraper = new ApiDocsScraper(usePlaywright, token);
            Path output = Paths.get(outputPath);

            if (allUrls.size() == 1) {
                scraper.scrapeAndExport(allUrls.get(0), output);
            } else {
                scraper.scrapeMultipleAndExport(allUrls, output);
            }

            logger.info("Scraping completed successfully");
            System.out.println("✓ Successfully scraped and exported to: " + outputPath);
            return 0;

        } catch (ScraperException e) {
            logger.error("Scraping failed", e);
            System.err.println("Error: " + e.getMessage());
            if (verbose && e.getCause() != null) {
                e.getCause().printStackTrace();
            }
            return 1;
        } catch (Exception e) {
            logger.error("Unexpected error", e);
            System.err.println("Unexpected error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private List<String> combineUrls() {
        if (urls != null && positionalUrls != null) {
            // Combine both
            urls.addAll(positionalUrls);
            return urls;
        } else if (urls != null) {
            return urls;
        } else if (positionalUrls != null) {
            return positionalUrls;
        }
        return List.of();
    }

    private void setLogLevel(String level) {
        // Note: This is a simplified approach
        // In production, you'd configure this via logback.xml
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) 
                LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.valueOf(level));
    }
}

