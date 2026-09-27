package com.clubhub.file;

import com.clubhub.file.FileDtos.FileView;
import com.clubhub.file.FileDtos.UploadRequest;
import com.clubhub.file.FileDtos.UploadTicket;
import com.clubhub.membership.ClubRole;
import com.clubhub.tenancy.CurrentMember;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Uploads (CORE+) and downloads. Downloads are 302 redirects from a stable API URL to a
 * short-lived pre-signed S3 URL: the link in the UI never expires, the S3 link always does.
 */
@RestController
public class FileController {

    private final FileService service;

    public FileController(FileService service) {
        this.service = service;
    }

    @PostMapping("/api/club/files/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@clubAuthz.atLeast('CORE')")
    public UploadTicket requestUpload(@Valid @RequestBody UploadRequest request) {
        return service.requestUpload(request, CurrentMember.get().orElseThrow().userId());
    }

    @PostMapping("/api/club/files/{fileId}/confirm")
    @PreAuthorize("@clubAuthz.atLeast('CORE')")
    public FileView confirm(@PathVariable UUID fileId) {
        return service.confirm(fileId);
    }

    @GetMapping("/api/club/files/{fileId}")
    public ResponseEntity<Void> download(@PathVariable UUID fileId) {
        return redirect(service.downloadUrl(fileId));
    }

    @GetMapping("/api/club/events/{eventId}/poster")
    public ResponseEntity<Void> memberPoster(@PathVariable Long eventId) {
        boolean core = CurrentMember.get().orElseThrow().role().atLeast(ClubRole.CORE);
        return redirect(service.posterUrl(eventId, true, core));
    }

    @GetMapping("/api/clubs/{slug}/events/{eventId}/poster")
    public ResponseEntity<Void> publicPoster(@PathVariable Long eventId) {
        return redirect(service.posterUrl(eventId, false, false));
    }

    private static ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
