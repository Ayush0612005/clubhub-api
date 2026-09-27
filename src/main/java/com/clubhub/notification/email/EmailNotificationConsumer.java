package com.clubhub.notification.email;

import com.clubhub.notification.DomainEvent;
import com.clubhub.notification.DomainEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Its own consumer group on the same topic as the in-app inbox: Kafka gives each group every event,
 * so a slow or failing email provider (retries, DLT) never delays in-app notifications.
 */
@Component
public class EmailNotificationConsumer {

    private final EmailNotificationService emails;
    private final JsonMapper json;

    public EmailNotificationConsumer(EmailNotificationService emails, JsonMapper json) {
        this.emails = emails;
        this.json = json;
    }

    @KafkaListener(topics = DomainEventPublisher.TOPIC, groupId = "clubhub-email")
    void onEvent(String payload) {
        emails.handle(json.readValue(payload, DomainEvent.class));
    }
}
