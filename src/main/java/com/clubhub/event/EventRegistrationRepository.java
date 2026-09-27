package com.clubhub.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {

    long countByEventId(Long eventId);

    Optional<EventRegistration> findByEventIdAndUserId(Long eventId, UUID userId);

    boolean existsByEventIdAndUserId(Long eventId, UUID userId);

    List<EventRegistration> findAllByEventIdOrderByRegisteredAtAsc(Long eventId);

    List<EventRegistration> findAllByUserId(UUID userId);

    /** Registration counts for many events in ONE grouped query (rows: [eventId, count]). */
    @Query("select r.eventId, count(r) from EventRegistration r where r.eventId in :eventIds group by r.eventId")
    List<Object[]> countByEventIds(Collection<Long> eventIds);
}
