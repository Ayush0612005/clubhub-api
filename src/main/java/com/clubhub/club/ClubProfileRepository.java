package com.clubhub.club;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClubProfileRepository extends JpaRepository<ClubProfile, Long> {

    /** One profile per club schema; created during provisioning. */
    Optional<ClubProfile> findFirstByOrderByIdAsc();
}
