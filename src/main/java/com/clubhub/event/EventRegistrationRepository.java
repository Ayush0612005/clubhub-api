package com.clubhub.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {

    long countByEventId(Long eventId);

    Optional<EventRegistration> findByEventIdAndUserId(Long eventId, UUID userId);

    boolean existsByEventIdAndUserId(Long eventId, UUID userId);

    List<EventRegistration> findAllByEventIdOrderByRegisteredAtAsc(Long eventId);

    List<EventRegistration> findAllByUserId(UUID userId);
}
