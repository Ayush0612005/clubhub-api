package com.clubhub.certificate;

import com.clubhub.certificate.CertificateDtos.CertificateView;
import com.clubhub.certificate.CertificateDtos.IssueResult;
import com.clubhub.certificate.CertificateDtos.Verification;
import com.clubhub.common.NotFoundException;
import com.clubhub.tenancy.CurrentMember;
import com.clubhub.tenancy.TenantContext;
import com.clubhub.tenant.Tenant;
import com.clubhub.tenant.TenantRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The three entrances to certificates: core team, recipient, and anyone verifying one. */
public final class CertificateControllers {

    private CertificateControllers() {
    }

    /** Core team of the active club. */
    @RestController
    @RequestMapping("/api/club")
    @PreAuthorize("@clubAuthz.atLeast('CORE')")
    public static class ClubCertificateController {

        private final CertificateService service;

        public ClubCertificateController(CertificateService service) {
            this.service = service;
        }

        @PostMapping("/events/{eventId}/certificates")
        public IssueResult issue(@PathVariable Long eventId) {
            return service.issueForEvent(eventId, CurrentMember.get().orElseThrow().userId());
        }

        @GetMapping("/events/{eventId}/certificates")
        public List<CertificateView> forEvent(@PathVariable Long eventId) {
            return service.forEvent(eventId);
        }

        @PostMapping("/certificates/{certificateId}/revoke")
        public CertificateView revoke(@PathVariable UUID certificateId) {
            return service.revoke(certificateId);
        }
    }

    /** The recipient, member or not (ClubBySlugFilter binds the club). */
    @RestController
    @RequestMapping("/api/clubs/{slug}/certificates")
    public static class MyCertificateController {

        private final CertificateService service;

        public MyCertificateController(CertificateService service) {
            this.service = service;
        }

        @GetMapping("/mine")
        public List<CertificateView> mine(@AuthenticationPrincipal Jwt jwt) {
            return service.mine(UUID.fromString(jwt.getSubject()));
        }

        @GetMapping("/{certificateId}/pdf")
        public ResponseEntity<byte[]> pdf(@PathVariable UUID certificateId, @AuthenticationPrincipal Jwt jwt) {
            byte[] pdf = service.pdf(certificateId, UUID.fromString(jwt.getSubject()));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("certificate-" + certificateId + ".pdf").build().toString())
                    .cacheControl(CacheControl.noStore())
                    .body(pdf);
        }
    }

    /**
     * Public, unauthenticated: the link/QR printed on a certificate, used by recruiters to check it.
     * The club comes from the URL; unknown club and unknown id both give 404.
     */
    @RestController
    @RequestMapping("/api/verify/certificates")
    public static class VerifyCertificateController {

        private final CertificateService service;
        private final TenantRepository tenants;

        public VerifyCertificateController(CertificateService service, TenantRepository tenants) {
            this.service = service;
            this.tenants = tenants;
        }

        @GetMapping("/{slug}/{certificateId}")
        public Verification verify(@PathVariable String slug, @PathVariable UUID certificateId) {
            Tenant club = Tenant.SLUG_PATTERN.matcher(slug).matches()
                    ? tenants.findBySlug(slug).orElseThrow(() -> new NotFoundException("No certificate with this ID"))
                    : null;
            if (club == null) {
                throw new NotFoundException("No certificate with this ID");
            }
            // a suspended club's certificates stay verifiable: they were real when issued
            return TenantContext.supplyAs(club.getSchemaName(), () -> service.verify(certificateId, club.getName()));
        }
    }
}
