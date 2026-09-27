package com.clubhub.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Relays committed domain events to Kafka.
 *
 * Trade-off (known, accepted for now): if the process dies between the DB commit and the Kafka
 * send, that one event is lost. The full fix is a transactional outbox table relayed to Kafka;
 * for notifications a rare miss is acceptable, for money it would not be.
 */
@Component
@OnKafkaTransport
public class KafkaEventRelay {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventRelay.class);

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    public KafkaEventRelay(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    /** Key = club id: all events of one club land on one partition, in order. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void relay(DomainEvent event) {
        kafka.send(DomainEventPublisher.TOPIC, event.club().tenantId().toString(), json.writeValueAsString(event))
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Could not publish {} {}", event.getClass().getSimpleName(), event.id(), error);
                    }
                });
    }
}
