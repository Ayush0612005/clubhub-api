package com.clubhub.plan;

import java.util.EnumSet;
import java.util.Set;

/**
 * Limits live in code, not in a table: they change with a release (and are reviewed like code),
 * while which plan a club is on is data. Per-club exceptions go through feature overrides.
 */
public enum Plan {

    FREE(100, 10, 2, 300, EnumSet.of(Feature.CERTIFICATES)),
    PRO(2000, 200, 50, 3000, EnumSet.allOf(Feature.class));

    private final int maxMembers;
    private final int maxUpcomingEvents;
    private final int maxOpenDrives;
    private final int requestsPerMinute;
    private final Set<Feature> features;

    Plan(int maxMembers, int maxUpcomingEvents, int maxOpenDrives, int requestsPerMinute, Set<Feature> features) {
        this.maxMembers = maxMembers;
        this.maxUpcomingEvents = maxUpcomingEvents;
        this.maxOpenDrives = maxOpenDrives;
        this.requestsPerMinute = requestsPerMinute;
        this.features = features;
    }

    public int maxMembers() { return maxMembers; }
    public int maxUpcomingEvents() { return maxUpcomingEvents; }
    public int maxOpenDrives() { return maxOpenDrives; }
    public int requestsPerMinute() { return requestsPerMinute; }

    public boolean includes(Feature feature) {
        return features.contains(feature);
    }
}
