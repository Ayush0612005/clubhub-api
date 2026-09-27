package com.clubhub.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Beans that only exist when domain events travel over Kafka ({@code clubhub.events.transport=kafka},
 * the default). With {@code in-process} they are skipped and {@link InProcessEventRelay} takes over.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "clubhub.events.transport", havingValue = "kafka", matchIfMissing = true)
public @interface OnKafkaTransport {
}
