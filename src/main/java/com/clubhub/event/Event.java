package com.clubhub.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Tenant-scoped (no schema in @Table): lives in the current club's schema. */
@Entity
@Table(name = "events")
public class Event {

    /** Doors open this long before the start: early arrivals can already be checked in. */
    public static final Duration CHECK_IN_OPENS_BEFORE = Duration.ofHours(1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Event() {
        // for JPA
    }

    public Event(String title, String description, String venue, Instant startsAt, Instant endsAt,
                 Integer capacity, EventVisibility visibility, UUID createdBy) {
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("An event must end after it starts");
        }
        this.title = title;
        this.description = description;
        this.venue = venue;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
        this.visibility = visibility;
        this.createdBy = createdBy;
        this.status = EventStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void publish() {
        if (status != EventStatus.DRAFT) {
            throw new IllegalStateException("Only a draft event can be published");
        }
        status = EventStatus.PUBLISHED;
        updatedAt = Instant.now();
    }

    public void cancel() {
        if (status == EventStatus.CANCELLED) {
            throw new IllegalStateException("Event is already cancelled");
        }
        status = EventStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    /** Registration closes when the event starts. */
    public boolean isOpenForRegistration(Instant now) {
        return status == EventStatus.PUBLISHED && now.isBefore(startsAt);
    }

    /** Check-in runs from shortly before the start until the end. */
    public boolean isCheckInOpen(Instant now) {
        return status == EventStatus.PUBLISHED
                && !now.isBefore(startsAt.minus(CHECK_IN_OPENS_BEFORE))
                && !now.isAfter(endsAt);
    }

    public boolean isVisibleTo(boolean member) {
        return status != EventStatus.DRAFT && (member || visibility == EventVisibility.PUBLIC);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getVenue() { return venue; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Integer getCapacity() { return capacity; }
    public EventVisibility getVisibility() { return visibility; }
    public EventStatus getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
