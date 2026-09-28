package com.clubhub.campus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Checks SRM's events feed every 6 hours while the app is running (on Render's free tier that means
 * whenever it's awake; admins also have an "Import now" button). Off by default so tests and local
 * development never call the real website.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "clubhub.campus.srm-feed.schedule-enabled", havingValue = "true")
public class SrmEventsSchedule {

    private static final Logger log = LoggerFactory.getLogger(SrmEventsSchedule.class);

    private final SrmEventsImporter importer;

    public SrmEventsSchedule(SrmEventsImporter importer) {
        this.importer = importer;
    }

    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT6H")
    void importSrmEvents() {
        try {
            importer.importNow();
        } catch (RuntimeException e) {
            // a website hiccup must never take the scheduler down; the next run retries
            log.warn("SRM events import failed: {}", e.getMessage());
        }
    }
}
