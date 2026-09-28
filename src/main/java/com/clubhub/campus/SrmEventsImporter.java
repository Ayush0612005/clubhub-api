package com.clubhub.campus;

import com.clubhub.campus.CampusDtos.ImportResult;
import com.clubhub.campus.SrmFeedParser.FeedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Pulls SRM's official events feed into the moderation queue. Uses the RSS feed the site publishes for
 * machines (allowed by its robots.txt), not the HTML pages, which sit behind bot protection.
 * Nothing is shown to students until an admin approves it.
 */
@Service
public class SrmEventsImporter {

    private static final Logger log = LoggerFactory.getLogger(SrmEventsImporter.class);

    private final CampusEventRepository events;
    private final String feedUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public SrmEventsImporter(CampusEventRepository events,
                             @Value("${clubhub.campus.srm-feed.url:https://www.srmist.edu.in/events/feed/}") String feedUrl) {
        this.events = events;
        this.feedUrl = feedUrl;
    }

    public ImportResult importNow() {
        return importFeed(fetch());
    }

    /** Separate from fetching so it can be tested with a saved feed, without the network. */
    @Transactional
    public ImportResult importFeed(String xml) {
        List<FeedItem> items = SrmFeedParser.parse(xml);
        LocalDate today = LocalDate.now(CampusService.CAMPUS_ZONE);
        int added = 0;
        int past = 0;
        int known = 0;
        for (FeedItem item : items) {
            if (events.existsByExternalId(item.guid())) {
                known++;
            } else if (item.endDate() != null && item.endDate().isBefore(today)) {
                past++; // reports of events that already happened ("was held on ...")
            } else {
                events.save(toPendingEvent(item));
                added++;
            }
        }
        log.info("SRM events feed: {} items, {} queued for review, {} past, {} already known", items.size(), added, past, known);
        return new ImportResult(items.size(), added, past, known);
    }

    private static CampusEvent toPendingEvent(FeedItem item) {
        CampusEvent event = new CampusEvent(truncate(item.title(), 200), CampusSource.SRM_FEED, ModerationStatus.PENDING);
        // the feed has no times: midnight campus time marks "date only" (the UI then hides the time)
        var zone = CampusService.CAMPUS_ZONE;
        var starts = item.startDate() == null ? null : item.startDate().atStartOfDay(zone).toInstant();
        var ends = item.endDate() == null || item.endDate().equals(item.startDate()) ? null
                : item.endDate().atTime(LocalTime.of(23, 59)).atZone(zone).toInstant();
        event.edit(null, truncate(item.title(), 200), truncate(item.text(), 2000), starts, ends, null,
                truncate(item.link(), 500), truncate(item.link(), 500));
        event.importedFrom(truncate(item.guid(), 300));
        return event;
    }

    private String fetch() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
                .timeout(Duration.ofSeconds(20))
                // honest identification, not a disguised browser
                .header("User-Agent", "ClubHub/1.0 (SRM KTR campus events directory)")
                .header("Accept", "application/rss+xml, application/xml;q=0.9")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 403) {
                // their bot protection; we don't try to get around it
                throw new IllegalArgumentException("srmist.edu.in blocked the automatic fetch (HTTP 403). "
                        + "Use \"Paste feed\" instead: open the feed in your browser and paste it.");
            }
            if (response.statusCode() != 200) {
                throw new IllegalStateException("SRM events feed answered HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach SRM events feed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while fetching SRM events feed", e);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
