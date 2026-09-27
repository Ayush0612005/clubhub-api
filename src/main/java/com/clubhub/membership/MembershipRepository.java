package com.clubhub.membership;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    /** The check behind every club-scoped request from Phase 2 on: is this user in this club, and as what? */
    Optional<Membership> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    List<Membership> findAllByUserId(UUID userId);

    List<Membership> findAllByTenantId(UUID tenantId);

    long countByTenantIdAndRole(UUID tenantId, ClubRole role);
}
