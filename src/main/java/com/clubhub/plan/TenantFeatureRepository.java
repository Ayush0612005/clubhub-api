package com.clubhub.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenantFeatureRepository extends JpaRepository<TenantFeature, TenantFeature.Key> {

    List<TenantFeature> findAllByTenantId(UUID tenantId);
}
