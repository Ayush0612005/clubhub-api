package com.clubhub.notification;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

    /** 3 partitions: room for 3 consumer instances; per-club ordering is kept by the club-id key. */
    @Bean
    NewTopic domainEventsTopic() {
        return TopicBuilder.name(DomainEventPublisher.TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic domainEventsDeadLetterTopic() {
        return TopicBuilder.name(DomainEventPublisher.TOPIC + "-dlt").partitions(3).replicas(1).build();
    }

    /**
     * A failing record is retried 3 times, 1 s apart, then parked on the dead-letter topic so one
     * poison message can't block its partition forever. Boot wires this into the listener factory.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, error) -> new TopicPartition(DomainEventPublisher.TOPIC + "-dlt", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
