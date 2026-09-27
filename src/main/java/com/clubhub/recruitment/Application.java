package com.clubhub.recruitment;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A student's application to one drive. Status only changes through {@link #moveTo}. */
@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drive_id", nullable = false, updatable = false)
    private Long driveId;

    @Column(name = "applicant_user_id", nullable = false, updatable = false)
    private UUID applicantUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ApplicationAnswer> answers = new ArrayList<>();

    protected Application() {
        // for JPA
    }

    public Application(Long driveId, UUID applicantUserId) {
        this.driveId = driveId;
        this.applicantUserId = applicantUserId;
        this.status = ApplicationStatus.APPLIED;
        this.submittedAt = Instant.now();
        this.updatedAt = this.submittedAt;
    }

    public void addAnswer(Long questionId, String answer) {
        answers.add(new ApplicationAnswer(this, questionId, answer));
    }

    /** Enforces the state machine; returns the previous status for the audit trail. */
    public ApplicationStatus moveTo(ApplicationStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("Cannot move application from " + status + " to " + target);
        }
        ApplicationStatus previous = status;
        this.status = target;
        this.updatedAt = Instant.now();
        return previous;
    }

    public Long getId() { return id; }
    public Long getDriveId() { return driveId; }
    public UUID getApplicantUserId() { return applicantUserId; }
    public ApplicationStatus getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ApplicationAnswer> getAnswers() { return answers; }
}
