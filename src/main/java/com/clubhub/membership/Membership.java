package com.clubhub.membership;

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
 * References User and Tenant by id instead of @ManyToOne: no lazy-loading surprises
 * (open-in-view is off) and the three aggregates stay independent.
 */
@Entity
@Table(name = "memberships", schema = "public")
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClubRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected Membership() {
        // for JPA
    }

    public Membership(UUID userId, UUID tenantId, ClubRole role) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public void changeRole(ClubRole newRole) {
        this.role = newRole;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public ClubRole getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
}
