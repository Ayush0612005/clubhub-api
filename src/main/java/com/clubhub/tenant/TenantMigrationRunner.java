package com.clubhub.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * On every startup, brings every active club schema up to the latest tenant migration.
 * Adding V2__*.sql to db/migration/tenant therefore reaches all clubs on the next deploy.
 *
 * Fail-fast: if one club's migration fails, startup fails. Serving traffic with clubs on
 * different schema versions would give confusing runtime errors instead of one clear one.
 */
@Component
public class TenantMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrationRunner.class);

    private final TenantRepository tenantRepository;
    private final TenantSchemaMigrator schemaMigrator;

    public TenantMigrationRunner(TenantRepository tenantRepository, TenantSchemaMigrator schemaMigrator) {
        this.tenantRepository = tenantRepository;
        this.schemaMigrator = schemaMigrator;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Tenant> tenants = tenantRepository.findAllByStatus(TenantStatus.ACTIVE);
        log.info("Migrating {} active tenant schema(s)", tenants.size());
        tenants.forEach(tenant -> schemaMigrator.migrate(tenant.getSchemaName()));
    }
}
