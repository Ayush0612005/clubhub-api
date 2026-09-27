package com.clubhub.certificate;

import java.time.Instant;
import java.util.UUID;

public final class CertificateDtos {

    private CertificateDtos() {
    }

    public record CertificateView(UUID id, Long eventId, String title, String recipientName, String description,
                                  Instant issuedAt, boolean revoked, String verifyUrl) {
    }

    public record IssueResult(Long eventId, int issued, int alreadyIssued) {
    }

    /** Public verification: only what is printed on the certificate itself, nothing more. */
    public record Verification(UUID id, boolean valid, String clubName, String title, String recipientName,
                               String description, Instant issuedAt, Instant revokedAt) {
    }
}
