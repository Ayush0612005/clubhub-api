package com.clubhub.auth;

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

/** A single-use link sent by email. The raw token only ever exists in the email; we keep its hash. */
@Entity
@Table(name = "email_tokens", schema = "public")
public class EmailToken {

    public enum Purpose { VERIFY_EMAIL, RESET_PASSWORD }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EmailToken() {
        // for JPA
    }

    EmailToken(UUID userId, Purpose purpose, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.purpose = purpose;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    void markUsed(Instant now) {
        this.usedAt = now;
    }

    public UUID getUserId() { return userId; }
    public Purpose getPurpose() { return purpose; }
}
