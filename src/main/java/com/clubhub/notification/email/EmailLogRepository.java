package com.clubhub.notification.email;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    boolean existsBySourceEventIdAndUserId(UUID sourceEventId, UUID userId);

    long countBySourceEventId(UUID sourceEventId);
}
