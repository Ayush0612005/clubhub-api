package com.clubhub.user;

import com.clubhub.common.NotFoundException;
import com.clubhub.membership.ClubRole;
import com.clubhub.membership.Membership;
import com.clubhub.membership.MembershipRepository;
import com.clubhub.plan.Plan;
import com.clubhub.tenant.Tenant;
import com.clubhub.tenant.TenantRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The signed-in user's own profile and club list (what the frontend needs after login). */
@RestController
@RequestMapping("/api/me")
public class MeController {

    public record Me(UUID id, String email, String fullName, boolean platformAdmin) {
    }

    public record MyClub(UUID tenantId, String slug, String name, ClubRole role, Plan plan) {
    }

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final TenantRepository tenants;

    public MeController(UserRepository users, MembershipRepository memberships, TenantRepository tenants) {
        this.users = users;
        this.memberships = memberships;
        this.tenants = tenants;
    }

    @GetMapping
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        User user = users.findById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new NotFoundException("Account not found"));
        return new Me(user.getId(), user.getEmail(), user.getFullName(),
                user.getPlatformRole() == PlatformRole.PLATFORM_ADMIN);
    }

    /** Active clubs the caller belongs to, with their role in each (two queries, no N+1). */
    @GetMapping("/clubs")
    @Transactional(readOnly = true)
    public List<MyClub> myClubs(@AuthenticationPrincipal Jwt jwt) {
        List<Membership> mine = memberships.findAllByUserId(UUID.fromString(jwt.getSubject()));
        Map<UUID, Tenant> byId = tenants.findAllById(mine.stream().map(Membership::getTenantId).toList()).stream()
                .collect(Collectors.toMap(Tenant::getId, Function.identity()));
        return mine.stream()
                .filter(m -> byId.containsKey(m.getTenantId()) && byId.get(m.getTenantId()).isActive())
                .map(m -> {
                    Tenant t = byId.get(m.getTenantId());
                    return new MyClub(t.getId(), t.getSlug(), t.getName(), m.getRole(), t.getPlan());
                })
                .sorted(Comparator.comparing(MyClub::name))
                .toList();
    }
}
