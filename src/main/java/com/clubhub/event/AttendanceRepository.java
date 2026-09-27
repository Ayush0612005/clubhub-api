package com.clubhub.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    long countByEventId(Long eventId);

    boolean existsByEventIdAndUserId(Long eventId, UUID userId);

    List<Attendance> findAllByEventIdOrderByCheckedInAtAsc(Long eventId);
}
