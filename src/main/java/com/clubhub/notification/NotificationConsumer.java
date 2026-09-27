package com.clubhub.notification;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads domain events from Kafka and records notifications. Runs outside any club context:
 * everything it needs (club id, slug, name) travels inside the event.
 * A failure is retried, then the record goes to the dead-letter topic (see KafkaConfig).
 */
@Component
public class NotificationConsumer {

    private final NotificationService notifications;
    private final JsonMapper json;

    public NotificationConsumer(NotificationService notifications, JsonMapper json) {
        this.notifications = notifications;
        this.json = json;
    }

    @KafkaListener(topics = DomainEventPublisher.TOPIC, groupId = "clubhub-notifications")
    void onEvent(String payload) {
        notifications.record(json.readValue(payload, DomainEvent.class));
    }
}
