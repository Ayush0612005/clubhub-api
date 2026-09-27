package com.clubhub.recruitment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped through the Hibernate session: always queries the current club's schema. */
public interface DriveRepository extends JpaRepository<RecruitmentDrive, Long> {

    List<RecruitmentDrive> findAllByOrderByCreatedAtDesc();

    List<RecruitmentDrive> findAllByStatusOrderByCreatedAtDesc(DriveStatus status);

    long countByStatus(DriveStatus status);

    /** Loads questions in the same query (fetch join) instead of a second lazy SELECT. */
    @EntityGraph(attributePaths = "questions")
    Optional<RecruitmentDrive> findWithQuestionsById(Long id);
}
