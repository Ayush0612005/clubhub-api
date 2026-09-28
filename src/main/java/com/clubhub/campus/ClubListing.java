package com.clubhub.campus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A club in the campus directory. Exists whether or not the club uses ClubHub: tenantId is set only
 * once the club "claims" the listing and gets its own workspace (unclaimed listings, like map pages).
 */
@Entity
@Table(name = "club_listings", schema = "public")
public class ClubListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 40)
    private String kind;

    @Column(length = 120)
    private String home;

    @Column(length = 500)
    private String description;

    @Column(name = "official_url", length = 500)
    private String officialUrl;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClubListing() {
        // for JPA
    }

    public ClubListing(String slug, String name, String category, String kind) {
        this.slug = slug;
        this.name = name;
        this.category = category;
        this.kind = kind;
        this.createdAt = Instant.now();
    }

    public void claim(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getKind() { return kind; }
    public String getHome() { return home; }
    public String getDescription() { return description; }
    public String getOfficialUrl() { return officialUrl; }
    public String getSourceUrl() { return sourceUrl; }
    public UUID getTenantId() { return tenantId; }
}
