package com.clubhub.tenancy;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Extracts the club slug a request is meant for.
 * Phase 1: {@link HeaderTenantResolver} (X-Tenant-ID). Phase 2: replaced by a JWT-claim
 * resolver; nothing else in the tenancy code changes.
 */
public interface TenantResolver {

    Optional<String> resolveSlug(HttpServletRequest request);
}
