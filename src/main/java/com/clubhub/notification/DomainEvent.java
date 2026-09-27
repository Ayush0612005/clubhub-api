package com.clubhub.notification;

import com.clubhub.tenant.Tenant;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened inside a club, published to Kafka after the transaction commits.
 * The JSON carries a "type" discriminator so one topic can hold every event kind.
 *
 * Every event has its own random {@link #id}: consumers use it to ignore redeliveries.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DomainEvent.ApplicationStatusChanged.class, name = "APPLICATION_STATUS_CHANGED"),
        @JsonSubTypes.Type(value = DomainEvent.CertificateIssued.class, name = "CERTIFICATE_ISSUED"),
        @JsonSubTypes.Type(value = DomainEvent.EventPublished.class, name = "EVENT_PUBLISHED")
})
public sealed interface DomainEvent {

    UUID id();

    Club club();

    Instant occurredAt();

    /** Club details copied into the event, so consumers never need to query club schemas. */
    record Club(UUID tenantId, String slug, String name) {

        public static Club of(Tenant tenant) {
            return new Club(tenant.getId(), tenant.getSlug(), tenant.getName());
        }
    }

    record ApplicationStatusChanged(UUID id, Club club, Instant occurredAt, UUID applicantId, long applicationId,
                                    String driveTitle, String status) implements DomainEvent {
    }

    record CertificateIssued(UUID id, Club club, Instant occurredAt, UUID recipientId, UUID certificateId,
                             String title) implements DomainEvent {
    }

    /** Fan-out event: the consumer notifies every current member of the club. */
    record EventPublished(UUID id, Club club, Instant occurredAt, long eventId, String eventTitle, String venue,
                          Instant startsAt) implements DomainEvent {
    }
}
