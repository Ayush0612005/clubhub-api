package com.clubhub.event;

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
@Table(name = "event_attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private AttendanceMethod method;

    @Column(name = "checked_in_by", nullable = false, updatable = false)
    private UUID checkedInBy;

    @Column(name = "checked_in_at", nullable = false, updatable = false)
    private Instant checkedInAt;

    protected Attendance() {
        // for JPA
    }

    public Attendance(Long eventId, UUID userId, AttendanceMethod method, UUID checkedInBy) {
        this.eventId = eventId;
        this.userId = userId;
        this.method = method;
        this.checkedInBy = checkedInBy;
        this.checkedInAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public UUID getUserId() { return userId; }
    public AttendanceMethod getMethod() { return method; }
    public UUID getCheckedInBy() { return checkedInBy; }
    public Instant getCheckedInAt() { return checkedInAt; }
}
