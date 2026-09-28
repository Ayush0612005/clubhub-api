package com.clubhub.campus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CampusEventRepository extends JpaRepository<CampusEvent, UUID> {

    /** Approved events that haven't finished before {@code from} (no end time: judged by the start). */
    @Query("""
            SELECT e FROM CampusEvent e
            WHERE e.status = com.clubhub.campus.ModerationStatus.APPROVED
              AND COALESCE(e.endsAt, e.startsAt) >= :from
            ORDER BY e.startsAt ASC""")
    List<CampusEvent> findUpcoming(@Param("from") Instant from);

    @Query("""
            SELECT e FROM CampusEvent e
            WHERE e.status = com.clubhub.campus.ModerationStatus.APPROVED
              AND COALESCE(e.endsAt, e.startsAt) >= :from
              AND e.clubListingId = :clubId
            ORDER BY e.startsAt ASC""")
    List<CampusEvent> findUpcomingForClub(@Param("from") Instant from, @Param("clubId") UUID clubId);

    List<CampusEvent> findAllByStatusOrderByCreatedAtAsc(ModerationStatus status);

    boolean existsByExternalId(String externalId);

    long countBySubmittedByAndStatus(UUID submittedBy, ModerationStatus status);
}
