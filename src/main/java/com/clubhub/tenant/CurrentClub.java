package com.clubhub.tenant;

import com.clubhub.tenancy.TenantContext;
import org.springframework.stereotype.Component;

/** The club (tenant row) whose schema is bound in the current TenantContext. */
@Component
public class CurrentClub {

    private final TenantRepository tenants;

    public CurrentClub(TenantRepository tenants) {
        this.tenants = tenants;
    }

    public Tenant get() {
        String schema = TenantContext.currentSchema()
                .orElseThrow(() -> new IllegalStateException("No club bound to this request"));
        return tenants.findBySchemaName(schema)
                .orElseThrow(() -> new IllegalStateException("No club for schema " + schema));
    }
}
