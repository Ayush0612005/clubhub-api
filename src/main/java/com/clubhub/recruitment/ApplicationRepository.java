package com.clubhub.recruitment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByDriveIdAndApplicantUserId(Long driveId, UUID applicantUserId);

    Page<Application> findAllByDriveId(Long driveId, Pageable pageable);

    Page<Application> findAllByDriveIdAndStatus(Long driveId, ApplicationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "answers")
    Optional<Application> findWithAnswersById(Long id);

    List<Application> findAllByApplicantUserIdOrderBySubmittedAtDesc(UUID applicantUserId);
}
