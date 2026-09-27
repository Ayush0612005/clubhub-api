package com.clubhub.auth;

import com.clubhub.auth.AuthExceptions.ClubAccessDeniedException;
import com.clubhub.membership.MembershipRepository;
import com.clubhub.security.JwtTokenService.ClubClaims;
import com.clubhub.tenant.Tenant;
import com.clubhub.tenant.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** The single place that decides "may this user act inside this club, and as what?". */
@Service
public class ClubAccessService {

    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;

    public ClubAccessService(TenantRepository tenantRepository, MembershipRepository membershipRepository) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ClubClaims> findAccess(UUID userId, String clubSlug) {
        return tenantRepository.findBySlug(clubSlug)
                .filter(Tenant::isActive)
                .flatMap(tenant -> membershipRepository.findByUserIdAndTenantId(userId, tenant.getId())
                        .map(m -> new ClubClaims(tenant.getId(), tenant.getSlug(), m.getRole().name())));
    }

    public ClubClaims requireAccess(UUID userId, String clubSlug) {
        return findAccess(userId, clubSlug).orElseThrow(ClubAccessDeniedException::new);
    }
}
