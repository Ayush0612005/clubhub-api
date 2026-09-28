package com.clubhub.campus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An event anywhere on campus. Registration happens on the organiser's own form (registrationUrl):
 * the organiser, not ClubHub, needs to see who registered.
 */
@Entity
@Table(name = "campus_events", schema = "public")
public class CampusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "club_listing_id")
    private UUID clubListingId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(length = 200)
    private String venue;

    @Column(name = "registration_url", length = 500)
    private String registrationUrl;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampusSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationStatus status;

    /** Feed item GUID; unique, so re-importing never duplicates an event. */
    @Column(name = "external_id", length = 300)
    private String externalId;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected CampusEvent() {
        // for JPA
    }

    public CampusEvent(String title, CampusSource source, ModerationStatus status) {
        this.title = title;
        this.source = source;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public void edit(UUID clubListingId, String title, String description, Instant startsAt, Instant endsAt,
                     String venue, String registrationUrl, String sourceUrl) {
        this.clubListingId = clubListingId;
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.venue = venue;
        this.registrationUrl = registrationUrl;
        this.sourceUrl = sourceUrl;
    }

    public void review(ModerationStatus decision) {
        if (decision == ModerationStatus.APPROVED && startsAt == null) {
            throw new IllegalArgumentException("Set the event's date before approving it");
        }
        this.status = decision;
        this.reviewedAt = Instant.now();
    }

    void importedFrom(String externalId) {
        this.externalId = externalId;
    }

    void submittedBy(UUID userId) {
        this.submittedBy = userId;
    }

    public UUID getId() { return id; }
    public UUID getClubListingId() { return clubListingId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getVenue() { return venue; }
    public String getRegistrationUrl() { return registrationUrl; }
    public String getSourceUrl() { return sourceUrl; }
    public CampusSource getSource() { return source; }
    public ModerationStatus getStatus() { return status; }
    public String getExternalId() { return externalId; }
    public UUID getSubmittedBy() { return submittedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
