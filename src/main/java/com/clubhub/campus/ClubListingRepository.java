package com.clubhub.campus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubListingRepository extends JpaRepository<ClubListing, UUID> {

    Optional<ClubListing> findBySlug(String slug);

    List<ClubListing> findAllByOrderByNameAsc();
}
