package com.clubhub.tenant;

import com.clubhub.plan.Plan;
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

@Entity
@Table(name = "tenants", schema = "public")
public class Tenant {

    public static final String SCHEMA_PREFIX = "club_";

    /** Must match ck_tenants_slug in V1__create_tenants.sql. */
    public static final java.util.regex.Pattern SLUG_PATTERN =
            java.util.regex.Pattern.compile("^[a-z][a-z0-9_]{2,39}$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false, length = 40)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "schema_name", nullable = false, unique = true, updatable = false, length = 63)
    private String schemaName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan;

    protected Tenant() {
        // for JPA
    }

    public Tenant(String slug, String name) {
        this(slug, name, Plan.FREE);
    }

    public Tenant(String slug, String name, Plan plan) {
        this.slug = slug;
        this.name = name;
        this.schemaName = SCHEMA_PREFIX + slug;
        this.status = TenantStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.plan = plan;
    }

    public void changePlan(Plan newPlan) {
        this.plan = newPlan;
    }

    public Plan getPlan() { return plan; }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getSchemaName() { return schemaName; }
    public TenantStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isActive() { return status == TenantStatus.ACTIVE; }
}
