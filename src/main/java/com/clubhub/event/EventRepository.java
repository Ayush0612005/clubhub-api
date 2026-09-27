package com.clubhub.event;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    /** Events that have not finished yet, soonest first. */
    List<Event> findAllByEndsAtAfterOrderByStartsAtAsc(Instant now);

    List<Event> findAllByOrderByStartsAtDesc();

    /** Plan usage: events that haven't finished and weren't cancelled. */
    long countByEndsAtAfterAndStatusNot(Instant now, EventStatus status);

    /**
     * SELECT ... FOR UPDATE on the event row. Registrations for the same event queue up behind
     * this lock, so "count < capacity, then insert" cannot oversell the last seat.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findForUpdateById(Long id);
}
