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
import java.time.LocalDate;
import java.util.UUID;

/** "Club X is recruiting for Y": students apply on the club's own form (applyUrl). */
@Entity
@Table(name = "campus_recruitments", schema = "public")
public class CampusRecruitment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "club_listing_id", nullable = false)
    private UUID clubListingId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "apply_url", length = 500)
    private String applyUrl;

    /** Last day to apply (inclusive, campus time); null = open until removed. */
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampusSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationStatus status;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected CampusRecruitment() {
        // for JPA
    }

    public CampusRecruitment(UUID clubListingId, String title, CampusSource source, ModerationStatus status) {
        this.clubListingId = clubListingId;
        this.title = title;
        this.source = source;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public void edit(UUID clubListingId, String title, String description, String applyUrl, LocalDate deadline) {
        this.clubListingId = clubListingId;
        this.title = title;
        this.description = description;
        this.applyUrl = applyUrl;
        this.deadline = deadline;
    }

    public void review(ModerationStatus decision) {
        this.status = decision;
        this.reviewedAt = Instant.now();
    }

    void submittedBy(UUID userId) {
        this.submittedBy = userId;
    }

    public UUID getId() { return id; }
    public UUID getClubListingId() { return clubListingId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getApplyUrl() { return applyUrl; }
    public LocalDate getDeadline() { return deadline; }
    public CampusSource getSource() { return source; }
    public ModerationStatus getStatus() { return status; }
    public UUID getSubmittedBy() { return submittedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
