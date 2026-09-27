package com.clubhub.event;

/** DRAFT (only the core team sees it) -> PUBLISHED -> CANCELLED. "Finished" is derived from ends_at. */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED
}
