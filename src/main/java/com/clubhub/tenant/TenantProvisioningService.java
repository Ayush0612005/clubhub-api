package com.clubhub.tenant;

import com.clubhub.club.ClubProfile;
import com.clubhub.club.ClubProfileRepository;
import com.clubhub.membership.ClubRole;
import com.clubhub.membership.Membership;
import com.clubhub.membership.MembershipRepository;
import com.clubhub.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.util.UUID;

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
    private final ClubProfileRepository clubProfileRepository;
    private final MembershipRepository membershipRepository;

    public TenantProvisioningService(TenantRepository tenantRepository,
                                     TenantSchemaMigrator schemaMigrator,
                                     ClubProfileRepository clubProfileRepository,
                                     MembershipRepository membershipRepository) {
        this.tenantRepository = tenantRepository;
        this.schemaMigrator = schemaMigrator;
        this.clubProfileRepository = clubProfileRepository;
        this.membershipRepository = membershipRepository;
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
        // seed the club's own data, written through the normal tenant-routed JPA path
        TenantContext.runAs(tenant.getSchemaName(), () -> clubProfileRepository.save(new ClubProfile(name)));
        // unique constraint still guards the race where two requests pass existsBySlug together
        return tenantRepository.saveAndFlush(tenant);
    }

    /** Provision a club and make {@code ownerUserId} its first CLUB_ADMIN. */
    public Tenant provision(String slug, String name, UUID ownerUserId) {
        Tenant tenant = provision(slug, name);
        membershipRepository.save(new Membership(ownerUserId, tenant.getId(), ClubRole.CLUB_ADMIN));
        return tenant;
    }
}
