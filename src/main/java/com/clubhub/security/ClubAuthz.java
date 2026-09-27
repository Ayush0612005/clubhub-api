package com.clubhub.security;

import com.clubhub.membership.ClubRole;
import com.clubhub.tenancy.CurrentMember;
import org.springframework.stereotype.Component;

/**
 * Used from SpEL in @PreAuthorize, e.g. {@code @PreAuthorize("@clubAuthz.atLeast('CORE')")}.
 * Reads the role TenantFilter loaded from the database for this request.
 */
@Component("clubAuthz")
public class ClubAuthz {

    public boolean atLeast(String requiredRole) {
        ClubRole required = ClubRole.valueOf(requiredRole); // typo in an annotation fails loudly, not silently
        return CurrentMember.get()
                .map(member -> member.role().atLeast(required))
                .orElse(false); // no club context = deny
    }
}
