package com.clubhub.tenant;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Applies db/migration/tenant to one club schema.
 * Spring Boot's auto-configured Flyway only handles "public"; this handles the N club schemas.
 */
@Component
public class TenantSchemaMigrator {

    static final String TENANT_MIGRATIONS = "classpath:db/migration/tenant";

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaMigrator.class);

    private final DataSource dataSource;

    public TenantSchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Creates the schema if missing, then applies pending migrations. Safe to call repeatedly. */
    public void migrate(String schemaName) {
        MigrateResult result = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)          // Flyway quotes the identifier: no SQL injection via schema name
                .defaultSchema(schemaName)    // history table lives inside the club schema
                .createSchemas(true)
                .locations(TENANT_MIGRATIONS)
                .load()
                .migrate();

        log.info("Tenant schema {} migrated: {} migration(s) applied, now at {}",
                schemaName, result.migrationsExecuted, result.targetSchemaVersion);
    }
}
