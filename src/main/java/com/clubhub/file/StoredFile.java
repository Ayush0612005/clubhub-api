package com.clubhub.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/** Tenant-scoped metadata of one S3 object. PENDING until the upload is confirmed. */
@Entity
@Table(name = "stored_files")
public class StoredFile implements Persistable<UUID> {

    public enum Status { PENDING, READY }

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "object_key", nullable = false, updatable = false, length = 300)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private FilePurpose purpose;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "event_id", updatable = false)
    private Long eventId;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected StoredFile() {
        // for JPA
    }

    public StoredFile(UUID id, String objectKey, FilePurpose purpose, String contentType, long sizeBytes,
                      Long eventId, UUID uploadedBy) {
        this.id = id;
        this.objectKey = objectKey;
        this.purpose = purpose;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.eventId = eventId;
        this.uploadedBy = uploadedBy;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public void markReady() {
        this.status = Status.READY;
        this.confirmedAt = Instant.now();
    }

    public boolean isReady() {
        return status == Status.READY;
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
    public String getObjectKey() { return objectKey; }
    public FilePurpose getPurpose() { return purpose; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Status getStatus() { return status; }
    public Long getEventId() { return eventId; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
