package com.clubhub.tenancy;

import com.clubhub.membership.ClubRole;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Who is acting inside the current club request, and with which role. Bound by TenantFilter from
 * the membership row it just loaded, so the role is always the CURRENT one from the database,
 * never the (possibly stale) role copied into the access token.
 */
public final class CurrentMember {

    public record ClubMember(UUID userId, UUID tenantId, ClubRole role) {
    }

    private static final ScopedValue<ClubMember> CURRENT = ScopedValue.newInstance();

    private CurrentMember() {
    }

    public static Optional<ClubMember> get() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    static <T> T callAs(ClubMember member, Callable<T> action) throws Exception {
        return ScopedValue.where(CURRENT, member).call(action::call);
    }
}
