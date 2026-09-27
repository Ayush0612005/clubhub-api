package com.clubhub.notification.email;

import com.clubhub.notification.DomainEvent;
import com.clubhub.notification.DomainEvent.ApplicationStatusChanged;
import com.clubhub.notification.DomainEvent.CertificateIssued;
import com.clubhub.notification.DomainEvent.EventPublished;
import com.clubhub.plan.Feature;
import com.clubhub.plan.PlanService;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Emails only what matters to one person (application decisions, certificates). "New event"
 * fan-outs stay in-app: emailing a whole club for every event would train students to ignore us.
 */
@Service
public class EmailNotificationService {

    record Email(UUID userId, String subject, String body) {
    }

    private final EmailSender sender;
    private final EmailLogRepository emailLog;
    private final UserRepository users;
    private final String appUrl;
    private final PlanService plans;

    public EmailNotificationService(EmailSender sender, EmailLogRepository emailLog, UserRepository users,
                                    PlanService plans, @Value("${clubhub.web.app-url}") String appUrl) {
        this.plans = plans;
        this.sender = sender;
        this.emailLog = emailLog;
        this.users = users;
        this.appUrl = appUrl.replaceAll("/+$", "");
    }

    /**
     * The log row is written in the same transaction as the send. If the send throws, the row rolls
     * back and Kafka retries; if it succeeds, a redelivery finds the row and skips. (A crash between
     * a successful send and the commit can still repeat one email: at-least-once, not exactly-once.)
     */
    @Transactional
    public boolean handle(DomainEvent event) {
        Optional<Email> email = compose(event);
        if (email.isEmpty() || emailLog.existsBySourceEventIdAndUserId(event.id(), email.get().userId())) {
            return false;
        }
        if (!plans.isEnabled(event.club().tenantId(), Feature.EMAIL_NOTIFICATIONS)) {
            return false; // the in-app notification still exists; email is a plan feature
        }
        Optional<User> recipient = users.findById(email.get().userId());
        if (recipient.isEmpty()) {
            return false; // account deleted meanwhile
        }
        emailLog.saveAndFlush(new EmailLog(event.id(), email.get().userId(), email.get().subject()));
        sender.send(recipient.get().getEmail(), email.get().subject(), email.get().body());
        return true;
    }

    private Optional<Email> compose(DomainEvent event) {
        return switch (event) {
            case ApplicationStatusChanged e -> Optional.of(new Email(e.applicantId(),
                    e.club().name() + ": your application is " + e.status().toLowerCase(Locale.ROOT),
                    """
                    Hi,

                    Your application for "%s" at %s is now %s.

                    See your applications: %s/clubs/%s/recruitment/applications/mine

                    ClubHub""".formatted(e.driveTitle(), e.club().name(), e.status().toLowerCase(Locale.ROOT),
                            appUrl, e.club().slug())));
            case CertificateIssued e -> Optional.of(new Email(e.recipientId(),
                    "Your certificate from " + e.club().name(),
                    """
                    Hi,

                    %s has issued you a %s.

                    Download it: %s/clubs/%s/certificates/%s

                    ClubHub""".formatted(e.club().name(), e.title(), appUrl, e.club().slug(), e.certificateId())));
            case EventPublished e -> Optional.empty();
        };
    }
}
