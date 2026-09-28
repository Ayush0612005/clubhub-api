package com.clubhub.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    /** Row lock: two clicks on the same link at once must not both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailToken> findByTokenHash(String tokenHash);

    boolean existsByUserIdAndPurposeAndCreatedAtAfter(UUID userId, EmailToken.Purpose purpose, Instant after);

    /** Sending a new link kills the older unused ones, so only the latest email works. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.purpose = :purpose AND t.usedAt IS NULL")
    int invalidateUnused(@Param("userId") UUID userId, @Param("purpose") EmailToken.Purpose purpose,
                         @Param("now") Instant now);
}
