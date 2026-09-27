package com.clubhub.notification;

import com.clubhub.common.NotFoundException;
import com.clubhub.common.PageResponse;
import com.clubhub.membership.Membership;
import com.clubhub.membership.MembershipRepository;
import com.clubhub.notification.DomainEvent.ApplicationStatusChanged;
import com.clubhub.notification.DomainEvent.CertificateIssued;
import com.clubhub.notification.DomainEvent.EventPublished;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Turns domain events into inbox entries, and serves the inbox. */
@Service
public class NotificationService {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

    public record NotificationView(Long id, String type, String title, String body, String link, Instant createdAt,
                                   boolean read) {

        static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
                    n.getCreatedAt(), n.getReadAt() != null);
        }
    }

    private final NotificationRepository notifications;
    private final MembershipRepository memberships;

    public NotificationService(NotificationRepository notifications, MembershipRepository memberships) {
        this.notifications = notifications;
        this.memberships = memberships;
    }

    /**
     * Idempotent: users already notified for this event id are skipped, and the unique key
     * (source_event_id, user_id) is the final guard. Returns the notifications actually created.
     */
    @Transactional
    public List<Notification> record(DomainEvent event) {
        List<Notification> wanted = switch (event) {
            case ApplicationStatusChanged e -> List.of(new Notification(e.applicantId(), e.club().tenantId(), e.id(),
                    "APPLICATION_STATUS_CHANGED",
                    "Application update from " + e.club().name(),
                    "Your application for \"" + e.driveTitle() + "\" is now " + readable(e.status()) + ".",
                    "/clubs/" + e.club().slug() + "/recruitment/applications/mine"));
            case CertificateIssued e -> List.of(new Notification(e.recipientId(), e.club().tenantId(), e.id(),
                    "CERTIFICATE_ISSUED",
                    "New certificate from " + e.club().name(),
                    e.title() + " is ready to download.",
                    "/clubs/" + e.club().slug() + "/certificates/" + e.certificateId()));
            case EventPublished e -> memberships.findAllByTenantId(e.club().tenantId()).stream()
                    .map(Membership::getUserId)
                    .map(userId -> new Notification(userId, e.club().tenantId(), e.id(), "EVENT_PUBLISHED",
                            "New event: " + e.eventTitle(),
                            e.club().name() + " · " + e.venue() + " · " + WHEN.format(e.startsAt()),
                            "/clubs/" + e.club().slug() + "/events/" + e.eventId()))
                    .toList();
        };
        Set<UUID> alreadyNotified = new HashSet<>(notifications.findUserIdsBySourceEventId(event.id()));
        List<Notification> fresh = wanted.stream().filter(n -> !alreadyNotified.contains(n.getUserId())).toList();
        return notifications.saveAll(fresh);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> inbox(UUID userId, boolean unreadOnly, Pageable pageable) {
        var page = unreadOnly
                ? notifications.findAllByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notifications.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.of(page, NotificationView::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationView markRead(Long id, UUID userId) {
        Notification n = notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        n.markRead();
        return NotificationView.from(n);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId, Instant.now());
    }

    private static String readable(String status) {
        return status.charAt(0) + status.substring(1).toLowerCase(Locale.ROOT);
    }
}
