package com.clubhub.tenant;

import org.springframework.stereotype.Service;

/**
 * Onboards a new club: validates the slug, builds its schema, then registers it.
 *
 * Order matters: the schema is migrated BEFORE the tenants row is saved, so a club
 * is never visible in the registry without its tables. If migration fails, nothing
 * is registered and the request can simply be retried (migrate is idempotent).
 */
@Service
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final TenantSchemaMigrator schemaMigrator;

    public TenantProvisioningService(TenantRepository tenantRepository, TenantSchemaMigrator schemaMigrator) {
        this.tenantRepository = tenantRepository;
        this.schemaMigrator = schemaMigrator;
    }

    public Tenant provision(String slug, String name) {
        // Validate here too, not only in the DB CHECK: the schema is created before the row is inserted
        if (slug == null || !Tenant.SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException(
                    "Invalid slug '" + slug + "': use 3-40 chars, lowercase letters, digits or _, starting with a letter");
        }
        if (tenantRepository.existsBySlug(slug)) {
            throw new TenantAlreadyExistsException(slug);
        }

        Tenant tenant = new Tenant(slug, name);
        schemaMigrator.migrate(tenant.getSchemaName());
        // unique constraint still guards the race where two requests pass existsBySlug together
        return tenantRepository.saveAndFlush(tenant);
    }
}
