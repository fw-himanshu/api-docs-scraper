package com.freshworks.scraper.fetcher;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Playwright-based page fetcher for JavaScript-rendered pages.
 */
public class PlaywrightFetcher implements PageFetcher {
    private static final Logger logger = LoggerFactory.getLogger(PlaywrightFetcher.class);
    private final Playwright playwright;
    private final Browser browser;
    private final int waitTimeMs;

    public PlaywrightFetcher() {
        this(5000); // Default 5 seconds wait
    }

    public PlaywrightFetcher(int waitTimeMs) {
        this.waitTimeMs = waitTimeMs;
        logger.info("Initializing Playwright browser...");
        
        // Set driver path if provided via environment variable
        String driverPath = System.getenv("PLAYWRIGHT_DRIVER_PATH");
        if (driverPath != null && !driverPath.isEmpty()) {
            logger.info("Using Playwright driver from: {}", driverPath);
            System.setProperty("PLAYWRIGHT_DRIVER_PATH", driverPath);
        }
        
        try {
            this.playwright = Playwright.create();
            this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true));
            logger.info("Playwright browser initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize Playwright", e);
            throw new RuntimeException("Failed to create Playwright browser: " + e.getMessage(), e);
        }
    }

    @Override
    public String fetch(String url) throws FetchException {
        return fetchWithRetry(url, 3);
    }
    
    private String fetchWithRetry(String url, int maxRetries) throws FetchException {
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < maxRetries) {
            attempts++;
            logger.info("Fetching URL with Playwright (attempt {}/{}): {}", attempts, maxRetries, url);
            
            Page page = null;
            try {
                // Check if browser is still open
                if (browser == null || !browser.isConnected()) {
                    throw new FetchException("Browser is not available or has been closed");
                }
                
                page = browser.newPage();
                logger.debug("   📄 New page created");
                
                // Navigate with timeout
                logger.debug("   🌐 Navigating to URL...");
                page.navigate(url, new Page.NavigateOptions().setTimeout(60000));
                logger.debug("   ✅ Navigation complete");
                
                // Wait for page to be loaded and JS to execute
                logger.debug("   ⏳ Waiting for load state...");
                page.waitForLoadState();
                logger.debug("   ✅ Load state reached");
                
                // Wait for network to be idle (important for dynamic content)
                try {
                    logger.debug("   ⏳ Waiting for network idle...");
                    page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                    logger.debug("   ✅ Network idle reached");
                } catch (Exception e) {
                    logger.debug("   ⚠️  Network idle wait timed out, continuing anyway");
                }
                
                // Scroll the page to trigger lazy-loaded content
                try {
                    logger.debug("   📜 Scrolling page to trigger lazy-loaded content...");
                    // Scroll down gradually to load navigation/sidebar content
                    for (int i = 0; i < 5; i++) {
                        page.evaluate("window.scrollBy(0, window.innerHeight / 2)");
                        Thread.sleep(500);
                    }
                    // Scroll back to top
                    page.evaluate("window.scrollTo(0, 0)");
                    Thread.sleep(500);
                    logger.debug("   ✅ Scrolling complete");
                } catch (Exception e) {
                    logger.debug("   ⚠️  Scrolling failed, continuing anyway: {}", e.getMessage());
                }
                
                // Additional wait for dynamic content
                try {
                    logger.debug("   ⏳ Waiting {} ms for dynamic content...", waitTimeMs);
                    Thread.sleep(waitTimeMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new FetchException("Fetch interrupted", e);
                }
                
                String content = page.content();
                logger.info("✅ Successfully fetched URL with Playwright (attempt {}): {}", attempts, url);
                logger.debug("   📊 Content length: {} characters", content.length());
                return content;
                
            } catch (com.microsoft.playwright.PlaywrightException e) {
                lastException = e;
                if (e.getMessage().contains("Target page, context or browser has been closed")) {
                    logger.warn("   ⚠️  Browser/page was closed during fetch (attempt {}/{})", attempts, maxRetries);
                    if (attempts < maxRetries) {
                        logger.info("   🔄 Retrying in 2 seconds...");
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                }
                logger.error("   ❌ Failed to fetch URL with Playwright (attempt {}): {}", attempts, e.getMessage());
                throw new FetchException("Failed to fetch URL with Playwright: " + url, e);
            } catch (Exception e) {
                lastException = e;
                logger.error("   ❌ Failed to fetch URL with Playwright (attempt {}): {}", attempts, e.getMessage());
                if (attempts == maxRetries) {
                    throw new FetchException("Failed to fetch URL with Playwright: " + url, e);
                } else {
                    logger.info("   🔄 Retrying in 2 seconds...");
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                if (page != null) {
                    try {
                        page.close();
                        logger.debug("   ✅ Page closed");
                    } catch (Exception e) {
                        logger.debug("   ⚠️  Error closing page: {}", e.getMessage());
                    }
                }
            }
        }
        
        // If we get here, all retries failed
        throw new FetchException("Failed to fetch URL after " + maxRetries + " attempts: " + url, lastException);
    }

    @Override
    public void close() {
        logger.info("Closing Playwright browser...");
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        logger.info("Playwright browser closed");
    }
}

