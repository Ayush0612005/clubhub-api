package com.clubhub.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which schema a new Session belongs to.
 * No tenant in scope (platform endpoints, startup) means the shared "public" schema.
 */
@Component
public class CurrentTenantSchemaResolver implements CurrentTenantIdentifierResolver<String> {

    public static final String DEFAULT_SCHEMA = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.currentSchema().orElse(DEFAULT_SCHEMA);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
