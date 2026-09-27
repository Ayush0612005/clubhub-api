package com.clubhub.certificate;

import com.clubhub.audit.AuditService;
import com.clubhub.certificate.CertificateDtos.CertificateView;
import com.clubhub.certificate.CertificateDtos.IssueResult;
import com.clubhub.certificate.CertificateDtos.Verification;
import com.clubhub.common.ConflictException;
import com.clubhub.common.NotFoundException;
import com.clubhub.event.Attendance;
import com.clubhub.event.AttendanceRepository;
import com.clubhub.event.Event;
import com.clubhub.event.EventRepository;
import com.clubhub.event.EventStatus;
import com.clubhub.notification.DomainEvent;
import com.clubhub.notification.DomainEventPublisher;
import com.clubhub.tenancy.TenantContext;
import com.clubhub.tenant.Tenant;
import com.clubhub.tenant.TenantRepository;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Issuing, listing, rendering and verifying certificates of the club bound in TenantContext. */
@Service
public class CertificateService {

    static final String PARTICIPATION_TITLE = "Certificate of Participation";
    private static final DateTimeFormatter EVENT_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final CertificateRepository certificates;
    private final EventRepository events;
    private final AttendanceRepository attendance;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final CertificatePdfRenderer renderer;
    private final String publicBaseUrl;
    private final DomainEventPublisher domainEvents;
    private final AuditService audit;

    public CertificateService(CertificateRepository certificates, EventRepository events,
                              AttendanceRepository attendance, UserRepository users, TenantRepository tenants,
                              CertificatePdfRenderer renderer, DomainEventPublisher domainEvents, AuditService audit,
                              @Value("${clubhub.public-base-url}") String publicBaseUrl) {
        this.domainEvents = domainEvents;
        this.audit = audit;
        this.certificates = certificates;
        this.events = events;
        this.attendance = attendance;
        this.users = users;
        this.tenants = tenants;
        this.renderer = renderer;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    /**
     * Issues a participation certificate to everyone who was checked in at the event. Idempotent:
     * attendees who already have one are skipped, so the core team can safely run it again after
     * late check-ins.
     */
    @Transactional
    public IssueResult issueForEvent(Long eventId, UUID issuerId) {
        Event event = events.findById(eventId).orElseThrow(() -> new NotFoundException("Event not found"));
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("Cannot issue certificates for a cancelled event");
        }
        List<Attendance> attendees = attendance.findAllByEventIdOrderByCheckedInAtAsc(eventId);
        if (attendees.isEmpty()) {
            throw new ConflictException("Nobody has been checked in at this event yet");
        }
        Set<UUID> alreadyIssued = certificates.findAllByEventId(eventId).stream()
                .map(Certificate::getUserId).collect(Collectors.toSet());
        Map<UUID, User> byId = users.findAllById(attendees.stream().map(Attendance::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        String description = "for participating in " + event.getTitle() + " on "
                + EVENT_DATE.format(event.getStartsAt().atZone(CertificatePdfRenderer.DISPLAY_ZONE));
        List<Certificate> fresh = attendees.stream()
                .map(Attendance::getUserId)
                .filter(userId -> !alreadyIssued.contains(userId) && byId.containsKey(userId))
                .map(userId -> new Certificate(userId, eventId, PARTICIPATION_TITLE,
                        byId.get(userId).getFullName(), description, issuerId))
                .toList();
        certificates.saveAll(fresh);
        audit.record("CERTIFICATES_ISSUED", "EVENT", eventId, Map.of("issued", fresh.size()));

        DomainEvent.Club club = DomainEvent.Club.of(currentClub());
        fresh.forEach(c -> domainEvents.publish(new DomainEvent.CertificateIssued(UUID.randomUUID(), club,
                Instant.now(), c.getUserId(), c.getId(), c.getTitle())));
        return new IssueResult(eventId, fresh.size(), alreadyIssued.size());
    }

    @Transactional(readOnly = true)
    public List<CertificateView> mine(UUID userId) {
        String slug = currentClub().getSlug();
        return certificates.findAllByUserIdOrderByIssuedAtDesc(userId).stream().map(c -> view(c, slug)).toList();
    }

    @Transactional(readOnly = true)
    public List<CertificateView> forEvent(Long eventId) {
        String slug = currentClub().getSlug();
        return certificates.findAllByEventId(eventId).stream().map(c -> view(c, slug)).toList();
    }

    /** The owner's PDF. Someone else's certificate id is "not found": ids reveal nothing. */
    @Transactional(readOnly = true)
    public byte[] pdf(UUID certificateId, UUID userId) {
        Certificate certificate = certificates.findById(certificateId)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Certificate not found"));
        if (certificate.isRevoked()) {
            throw new ConflictException("This certificate has been revoked");
        }
        Tenant club = currentClub();
        return renderer.render(certificate, club.getName(), verifyUrl(club.getSlug(), certificateId));
    }

    @Transactional
    public CertificateView revoke(UUID certificateId) {
        Certificate certificate = certificates.findById(certificateId)
                .orElseThrow(() -> new NotFoundException("Certificate not found"));
        try {
            certificate.revoke();
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        audit.record("CERTIFICATE_REVOKED", "CERTIFICATE", certificateId,
                Map.of("recipient", certificate.getRecipientName()));
        return view(certificate, currentClub().getSlug());
    }

    @Transactional(readOnly = true)
    public Verification verify(UUID certificateId, String clubName) {
        Certificate c = certificates.findById(certificateId)
                .orElseThrow(() -> new NotFoundException("No certificate with this ID"));
        return new Verification(c.getId(), !c.isRevoked(), clubName, c.getTitle(), c.getRecipientName(),
                c.getDescription(), c.getIssuedAt(), c.getRevokedAt());
    }

    String verifyUrl(String slug, UUID certificateId) {
        return publicBaseUrl + "/api/verify/certificates/" + slug + "/" + certificateId;
    }

    private CertificateView view(Certificate c, String slug) {
        return new CertificateView(c.getId(), c.getEventId(), c.getTitle(), c.getRecipientName(), c.getDescription(),
                c.getIssuedAt(), c.isRevoked(), verifyUrl(slug, c.getId()));
    }

    private Tenant currentClub() {
        String schema = TenantContext.currentSchema().orElseThrow();
        return tenants.findBySchemaName(schema).orElseThrow();
    }
}
