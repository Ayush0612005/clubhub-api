package com.clubhub.campus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CampusRecruitmentRepository extends JpaRepository<CampusRecruitment, UUID> {

    @Query("""
            SELECT r FROM CampusRecruitment r
            WHERE r.status = com.clubhub.campus.ModerationStatus.APPROVED
              AND (r.deadline IS NULL OR r.deadline >= :today)
            ORDER BY r.deadline ASC NULLS LAST, r.createdAt DESC""")
    List<CampusRecruitment> findOpen(@Param("today") LocalDate today);

    @Query("""
            SELECT r FROM CampusRecruitment r
            WHERE r.status = com.clubhub.campus.ModerationStatus.APPROVED
              AND (r.deadline IS NULL OR r.deadline >= :today)
              AND r.clubListingId = :clubId
            ORDER BY r.deadline ASC NULLS LAST, r.createdAt DESC""")
    List<CampusRecruitment> findOpenForClub(@Param("today") LocalDate today, @Param("clubId") UUID clubId);

    List<CampusRecruitment> findAllByStatusOrderByCreatedAtAsc(ModerationStatus status);

    long countBySubmittedByAndStatus(UUID submittedBy, ModerationStatus status);
}
