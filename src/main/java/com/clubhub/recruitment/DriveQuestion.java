package com.clubhub.recruitment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "drive_questions")
public class DriveQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drive_id", nullable = false, updatable = false)
    private RecruitmentDrive drive;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 500)
    private String prompt;

    @Column(nullable = false)
    private boolean required;

    protected DriveQuestion() {
        // for JPA
    }

    DriveQuestion(RecruitmentDrive drive, int sortOrder, String prompt, boolean required) {
        this.drive = drive;
        this.sortOrder = sortOrder;
        this.prompt = prompt;
        this.required = required;
    }

    public Long getId() { return id; }
    public int getSortOrder() { return sortOrder; }
    public String getPrompt() { return prompt; }
    public boolean isRequired() { return required; }
}
