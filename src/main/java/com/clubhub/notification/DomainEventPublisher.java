package com.clubhub.notification;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Services call {@link #publish} inside their transaction. The event is only handed on AFTER that
 * transaction commits, so a rolled-back change never produces a notification.
 *
 * Where it goes next is a deployment choice ({@code clubhub.events.transport}):
 * <ul>
 *   <li>{@code kafka} (default): {@link KafkaEventRelay} sends it to the {@link #TOPIC} topic, read by
 *       independent consumer groups for the inbox and for email (retries, dead-letter topic, scales
 *       to many instances);</li>
 *   <li>{@code in-process}: {@link InProcessEventRelay} calls the same handlers directly, for a single
 *       instance without a broker (free hosting).</li>
 * </ul>
 * Services never know which one is active.
 */
@Component
public class DomainEventPublisher {

    public static final String TOPIC = "clubhub.domain-events";

    private final ApplicationEventPublisher springEvents;

    public DomainEventPublisher(ApplicationEventPublisher springEvents) {
        this.springEvents = springEvents;
    }

    public void publish(DomainEvent event) {
        springEvents.publishEvent(event);
    }
}
