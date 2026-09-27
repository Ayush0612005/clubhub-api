package com.clubhub.notification;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

    /**
     * Default 3 partitions: room for 3 consumer instances; per-club ordering is kept by the club-id key.
     * Configurable because hosted free tiers cap partitions (Aiven free: 2 per topic). The dead-letter
     * topic gets the same count, since a failed record keeps its partition number there.
     */
    @Bean
    NewTopic domainEventsTopic(@Value("${clubhub.kafka.partitions:3}") int partitions,
                               @Value("${clubhub.kafka.replicas:0}") int replicas) {
        return topic(DomainEventPublisher.TOPIC, partitions, replicas);
    }

    @Bean
    NewTopic domainEventsDeadLetterTopic(@Value("${clubhub.kafka.partitions:3}") int partitions,
                                         @Value("${clubhub.kafka.replicas:0}") int replicas) {
        return topic(DomainEventPublisher.TOPIC + "-dlt", partitions, replicas);
    }

    /** {@code replicas <= 0} means "use the broker's default.replication.factor" (1 locally, 3 on hosted clusters). */
    private static NewTopic topic(String name, int partitions, int replicas) {
        TopicBuilder builder = TopicBuilder.name(name).partitions(partitions);
        return (replicas > 0 ? builder.replicas(replicas) : builder).build();
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
