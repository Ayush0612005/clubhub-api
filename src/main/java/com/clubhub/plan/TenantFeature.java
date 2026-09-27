package com.clubhub.plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A per-club override of one feature (public schema, keyed by tenant + feature). */
@Entity
@Table(name = "tenant_features", schema = "public")
@IdClass(TenantFeature.Key.class)
public class TenantFeature {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private Feature feature;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TenantFeature() {
        // for JPA
    }

    public TenantFeature(UUID tenantId, Feature feature, boolean enabled) {
        this.tenantId = tenantId;
        this.feature = feature;
        set(enabled);
    }

    public void set(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public Feature getFeature() { return feature; }
    public boolean isEnabled() { return enabled; }

    public static class Key implements Serializable {
        private UUID tenantId;
        private Feature feature;

        public Key() {
        }

        public Key(UUID tenantId, Feature feature) {
            this.tenantId = tenantId;
            this.feature = feature;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(tenantId, k.tenantId) && feature == k.feature;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, feature);
        }
    }
}
