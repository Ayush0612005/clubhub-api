package com.clubhub.notification.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_log", schema = "public")
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_event_id", nullable = false, updatable = false)
    private UUID sourceEventId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false, length = 200)
    private String subject;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected EmailLog() {
        // for JPA
    }

    public EmailLog(UUID sourceEventId, UUID userId, String subject) {
        this.sourceEventId = sourceEventId;
        this.userId = userId;
        this.subject = subject;
        this.sentAt = Instant.now();
    }

    public UUID getSourceEventId() { return sourceEventId; }
    public UUID getUserId() { return userId; }
    public String getSubject() { return subject; }
}
