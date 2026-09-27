package com.clubhub.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Services call {@link #publish} inside their transaction; the event goes to Kafka only AFTER
 * that transaction commits. So a rolled-back change never produces a notification.
 *
 * Trade-off (known, accepted for now): if the process dies between the DB commit and the Kafka
 * send, that one event is lost. The full fix is a transactional outbox table relayed to Kafka;
 * for notifications a rare miss is acceptable, for money it would not be.
 */
@Component
public class DomainEventPublisher {

    public static final String TOPIC = "clubhub.domain-events";

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final ApplicationEventPublisher springEvents;
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    public DomainEventPublisher(ApplicationEventPublisher springEvents, KafkaTemplate<String, String> kafka,
                                JsonMapper json) {
        this.springEvents = springEvents;
        this.kafka = kafka;
        this.json = json;
    }

    public void publish(DomainEvent event) {
        springEvents.publishEvent(event);
    }

    /** Key = club id: all events of one club land on one partition, in order. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void relayToKafka(DomainEvent event) {
        kafka.send(TOPIC, event.club().tenantId().toString(), json.writeValueAsString(event))
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Could not publish {} {}", event.getClass().getSimpleName(), event.id(), error);
                    }
                });
    }
}
