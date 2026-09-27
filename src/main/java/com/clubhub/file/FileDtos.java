package com.clubhub.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class FileDtos {

    private FileDtos() {
    }

    public record UploadRequest(
            @NotNull FilePurpose purpose,
            Long eventId,
            @NotBlank String contentType,
            @Positive long sizeBytes) {
    }

    /** The client PUTs the bytes to uploadUrl with exactly these headers, then calls confirm. */
    public record UploadTicket(UUID fileId, URI uploadUrl, String method, Map<String, String> requiredHeaders,
                               Instant expiresAt) {
    }

    public record FileView(UUID id, FilePurpose purpose, String contentType, long sizeBytes,
                           StoredFile.Status status, Long eventId) {

        static FileView from(StoredFile f) {
            return new FileView(f.getId(), f.getPurpose(), f.getContentType(), f.getSizeBytes(), f.getStatus(),
                    f.getEventId());
        }
    }
}
