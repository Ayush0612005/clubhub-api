package com.clubhub.certificate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/** Tenant-scoped. Recipient name is a snapshot: a later profile rename doesn't rewrite history. */
@Entity
@Table(name = "certificates")
public class Certificate implements Persistable<UUID> {

    @Id
    private UUID id;

    /**
     * The id is assigned in Java, so Spring Data can't tell new from existing by "id == null" and
     * would call merge() (an extra SELECT per insert). Persistable tells it explicitly.
     */
    @Transient
    private boolean isNew = true;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "event_id", updatable = false)
    private Long eventId;

    @Column(nullable = false, length = 150, updatable = false)
    private String title;

    @Column(name = "recipient_name", nullable = false, length = 100, updatable = false)
    private String recipientName;

    @Column(nullable = false, length = 500, updatable = false)
    private String description;

    @Column(name = "issued_by", nullable = false, updatable = false)
    private UUID issuedBy;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Certificate() {
        // for JPA
    }

    public Certificate(UUID userId, Long eventId, String title, String recipientName, String description,
                       UUID issuedBy) {
        this.id = UUID.randomUUID(); // random v4: 122 bits, safe to print as a public verification code
        this.userId = userId;
        this.eventId = eventId;
        this.title = title;
        this.recipientName = recipientName;
        this.description = description;
        this.issuedBy = issuedBy;
        this.issuedAt = Instant.now();
    }

    public void revoke() {
        if (revokedAt != null) {
            throw new IllegalStateException("Certificate is already revoked");
        }
        revokedAt = Instant.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Long getEventId() { return eventId; }
    public String getTitle() { return title; }
    public String getRecipientName() { return recipientName; }
    public String getDescription() { return description; }
    public UUID getIssuedBy() { return issuedBy; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
