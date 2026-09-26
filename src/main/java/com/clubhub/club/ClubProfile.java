package com.clubhub.club;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Tenant-scoped entity: NO schema in @Table on purpose. The table is resolved in whichever
 * club schema the current Hibernate session was opened for (see TenantContext).
 */
@Entity
@Table(name = "club_profile")
public class ClubProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClubProfile() {
        // for JPA
    }

    public ClubProfile(String displayName) {
        this.displayName = displayName;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String displayName, String description, String contactEmail) {
        this.displayName = displayName;
        this.description = description;
        this.contactEmail = contactEmail;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getContactEmail() { return contactEmail; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
