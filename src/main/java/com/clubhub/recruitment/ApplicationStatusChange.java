package com.clubhub.recruitment;

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

/** Append-only history row: never updated or deleted by the application. */
@Entity
@Table(name = "application_status_changes")
public class ApplicationStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private ApplicationStatus toStatus;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    @Column(length = 500, updatable = false)
    private String note;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected ApplicationStatusChange() {
        // for JPA
    }

    public ApplicationStatusChange(Long applicationId, ApplicationStatus fromStatus, ApplicationStatus toStatus,
                                   UUID changedBy, String note) {
        this.applicationId = applicationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.note = note;
        this.changedAt = Instant.now();
    }

    public Long getApplicationId() { return applicationId; }
    public ApplicationStatus getFromStatus() { return fromStatus; }
    public ApplicationStatus getToStatus() { return toStatus; }
    public UUID getChangedBy() { return changedBy; }
    public String getNote() { return note; }
    public Instant getChangedAt() { return changedAt; }
}
