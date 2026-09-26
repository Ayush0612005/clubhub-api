package com.clubhub.tenancy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * TEMPORARY (Phase 1 only): trusts a client-supplied header, so any caller can pick any club.
 * Must be deleted when the JWT resolver lands in Phase 2.
 */
@Component
public class HeaderTenantResolver implements TenantResolver {

    public static final String HEADER = "X-Tenant-ID";

    @Override
    public Optional<String> resolveSlug(HttpServletRequest request) {
        String value = request.getHeader(HEADER);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value.trim());
    }
}
