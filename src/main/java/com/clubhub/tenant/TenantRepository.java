package com.clubhub.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Which club the current TenantContext schema belongs to (e.g. for its name and slug). */
    Optional<Tenant> findBySchemaName(String schemaName);

    // used at startup to migrate every active club schema (step 1.3)
    List<Tenant> findAllByStatus(TenantStatus status);
}
