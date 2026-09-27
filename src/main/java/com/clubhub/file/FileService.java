package com.clubhub.file;

import com.clubhub.common.ConflictException;
import com.clubhub.common.NotFoundException;
import com.clubhub.event.Event;
import com.clubhub.event.EventRepository;
import com.clubhub.file.FileDtos.FileView;
import com.clubhub.file.FileDtos.UploadRequest;
import com.clubhub.file.FileDtos.UploadTicket;
import com.clubhub.file.FileStorage.ObjectInfo;
import com.clubhub.plan.Feature;
import com.clubhub.plan.PlanService;
import com.clubhub.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Direct-to-S3 uploads in three steps:
 * 1. requestUpload: validate type/size, record a PENDING row, hand out a pre-signed PUT URL
 * 2. the client uploads the bytes straight to S3 (the API server never touches them)
 * 3. confirm: check with S3 that the object really exists with the declared size and type,
 *    then mark it READY and attach it (e.g. as an event's poster)
 */
@Service
public class FileService {

    private final StoredFileRepository files;
    private final EventRepository events;
    private final FileStorage storage;
    private final StorageProperties props;
    private final PlanService plans;

    public FileService(StoredFileRepository files, EventRepository events, FileStorage storage,
                       StorageProperties props, PlanService plans) {
        this.files = files;
        this.events = events;
        this.storage = storage;
        this.props = props;
        this.plans = plans;
    }

    @Transactional
    public UploadTicket requestUpload(UploadRequest request, UUID uploaderId) {
        FilePurpose purpose = request.purpose();
        String contentType = request.contentType().toLowerCase(Locale.ROOT).strip();
        if (!purpose.allows(contentType)) {
            throw new IllegalArgumentException("Allowed file types: " + String.join(", ", purpose.allowedContentTypes()));
        }
        if (request.sizeBytes() > props.maxUploadBytes()) {
            throw new IllegalArgumentException("File is too large (max " + props.maxUploadBytes() / (1024 * 1024) + " MB)");
        }
        if (purpose == FilePurpose.EVENT_POSTER) {
            plans.requireFeature(Feature.EVENT_POSTERS);
            if (request.eventId() == null) {
                throw new IllegalArgumentException("eventId is required for an event poster");
            }
            events.findById(request.eventId()).orElseThrow(() -> new NotFoundException("Event not found"));
        }

        UUID id = UUID.randomUUID();
        // the club schema in the key keeps every club's objects under its own prefix
        String key = "clubs/%s/%s/%s.%s".formatted(TenantContext.currentSchema().orElseThrow(),
                purpose.name().toLowerCase(Locale.ROOT), id, purpose.extensionFor(contentType));
        files.save(new StoredFile(id, key, purpose, contentType, request.sizeBytes(), request.eventId(), uploaderId));

        URI url = storage.presignUpload(key, contentType, request.sizeBytes());
        return new UploadTicket(id, url, "PUT", Map.of("Content-Type", contentType),
                Instant.now().plus(props.uploadUrlTtl()));
    }

    @Transactional
    public FileView confirm(UUID fileId) {
        StoredFile file = files.findById(fileId).orElseThrow(() -> new NotFoundException("File not found"));
        if (file.isReady()) {
            return FileView.from(file); // idempotent: a retried confirm is harmless
        }
        ObjectInfo uploaded = storage.head(file.getObjectKey())
                .orElseThrow(() -> new ConflictException("The file has not been uploaded yet"));
        if (uploaded.sizeBytes() != file.getSizeBytes() || !file.getContentType().equals(uploaded.contentType())) {
            throw new ConflictException("The uploaded file does not match the declared size and type");
        }
        file.markReady();
        if (file.getPurpose() == FilePurpose.EVENT_POSTER) {
            events.findById(file.getEventId()).ifPresent(event -> event.attachPoster(file.getId()));
        }
        return FileView.from(file);
    }

    /** A short-lived download link; the stable API URL redirects to it. */
    @Transactional(readOnly = true)
    public URI downloadUrl(UUID fileId) {
        StoredFile file = files.findById(fileId).filter(StoredFile::isReady)
                .orElseThrow(() -> new NotFoundException("File not found"));
        return storage.presignDownload(file.getObjectKey());
    }

    @Transactional(readOnly = true)
    public URI posterUrl(Long eventId, boolean member, boolean core) {
        Event event = events.findById(eventId)
                .filter(e -> core || e.isVisibleTo(member))
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (event.getPosterFileId() == null) {
            throw new NotFoundException("This event has no poster");
        }
        return downloadUrl(event.getPosterFileId());
    }
}
