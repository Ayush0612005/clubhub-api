package com.clubhub.notification;

import com.clubhub.notification.email.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Broker-less transport ({@code clubhub.events.transport=in-process}): after the business transaction
 * commits, the same handlers the Kafka consumers use are called directly.
 *
 * Runs on its own (virtual) thread, for two reasons:
 * <ul>
 *   <li>the HTTP request doesn't wait for inbox writes, WebSocket pushes or an email provider;</li>
 *   <li>code running inside an AFTER_COMMIT callback on the same thread would join the already
 *       finished transaction, so its {@code @Transactional} writes would silently never commit.</li>
 * </ul>
 * Inbox and email are handled independently: an email failure never loses the in-app notification.
 * Compared with Kafka there are no retries, no dead-letter topic and no fan-out across instances, so
 * this fits a single-instance deployment.
 */
@Component
@ConditionalOnProperty(name = "clubhub.events.transport", havingValue = "in-process")
public class InProcessEventRelay implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(InProcessEventRelay.class);

    private final NotificationService notifications;
    private final NotificationPusher pusher;
    private final EmailNotificationService emails;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public InProcessEventRelay(NotificationService notifications, NotificationPusher pusher,
                               EmailNotificationService emails) {
        this.notifications = notifications;
        this.pusher = pusher;
        this.emails = emails;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void relay(DomainEvent event) {
        executor.execute(() -> deliver(event));
    }

    void deliver(DomainEvent event) {
        try {
            pusher.push(notifications.record(event)); // store first (committed), then push
        } catch (RuntimeException e) {
            log.error("In-app notification failed for {} {}", event.getClass().getSimpleName(), event.id(), e);
        }
        try {
            emails.handle(event);
        } catch (RuntimeException e) {
            log.error("Email notification failed for {} {}", event.getClass().getSimpleName(), event.id(), e);
        }
    }

    @Override
    public void destroy() {
        executor.close(); // waits for in-flight deliveries on shutdown
    }
}
