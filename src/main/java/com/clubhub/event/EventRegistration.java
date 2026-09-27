package com.clubhub.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_registrations")
public class EventRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    protected EventRegistration() {
        // for JPA
    }

    public EventRegistration(Long eventId, UUID userId) {
        this.eventId = eventId;
        this.userId = userId;
        this.registeredAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public Instant getRegisteredAt() { return registeredAt; }
}
