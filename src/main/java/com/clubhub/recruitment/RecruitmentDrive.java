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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tenant-scoped (no schema in @Table): lives in the current club's schema. */
@Entity
@Table(name = "recruitment_drives")
public class RecruitmentDrive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DriveStatus status;

    @Column(name = "closes_at")
    private Instant closesAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** The drive owns its questions: saved, updated and deleted together (aggregate root). */
    @OneToMany(mappedBy = "drive", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<DriveQuestion> questions = new ArrayList<>();

    protected RecruitmentDrive() {
        // for JPA
    }

    public RecruitmentDrive(String title, String description, Instant closesAt, UUID createdBy) {
        this.title = title;
        this.description = description;
        this.closesAt = closesAt;
        this.createdBy = createdBy;
        this.status = DriveStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void addQuestion(String prompt, boolean required) {
        questions.add(new DriveQuestion(this, questions.size() + 1, prompt, required));
    }

    public void changeStatus(DriveStatus newStatus) {
        if (!status.canMoveTo(newStatus)) {
            throw new IllegalStateException("Cannot move drive from " + status + " to " + newStatus);
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    /** Open for applications right now: status OPEN and deadline (if any) not passed. */
    public boolean isAcceptingApplications(Instant now) {
        return status == DriveStatus.OPEN && (closesAt == null || now.isBefore(closesAt));
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public DriveStatus getStatus() { return status; }
    public Instant getClosesAt() { return closesAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<DriveQuestion> getQuestions() { return questions; }
}
