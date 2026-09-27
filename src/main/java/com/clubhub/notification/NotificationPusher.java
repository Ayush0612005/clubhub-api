package com.clubhub.notification;

import com.clubhub.notification.NotificationService.NotificationView;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Pushes freshly stored notifications to the recipients' open WebSocket sessions (if any). */
@Component
public class NotificationPusher {

    static final String QUEUE = "/queue/notifications";

    private final SimpMessagingTemplate messaging;

    public NotificationPusher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /** Offline users simply get nothing pushed: the inbox API still has everything. */
    public void push(List<Notification> created) {
        created.forEach(n ->
                messaging.convertAndSendToUser(n.getUserId().toString(), QUEUE, NotificationView.from(n)));
    }
}
