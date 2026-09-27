package com.clubhub.membership;

import com.clubhub.audit.AuditService;
import com.clubhub.common.ConflictException;
import com.clubhub.common.NotFoundException;
import com.clubhub.membership.MemberDtos.MemberResponse;
import com.clubhub.plan.PlanService;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages who is in a club. The tenantId always comes from the caller's verified club context
 * (CurrentMember), never from the request body, so an admin can only manage their own club.
 */
@Service
public class MembershipService {

    private final MembershipRepository memberships;
    private final UserRepository users;
    private final AuditService audit;
    private final PlanService plans;

    public MembershipService(MembershipRepository memberships, UserRepository users, AuditService audit,
                             PlanService plans) {
        this.memberships = memberships;
        this.users = users;
        this.audit = audit;
        this.plans = plans;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(UUID tenantId) {
        List<Membership> all = memberships.findAllByTenantId(tenantId);
        // one query for all users instead of one per membership (avoids N+1)
        Map<UUID, User> byId = users.findAllById(all.stream().map(Membership::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return all.stream()
                .map(m -> toResponse(m, byId.get(m.getUserId())))
                .sorted(Comparator.comparing(MemberResponse::role).thenComparing(MemberResponse::fullName))
                .toList();
    }

    @Transactional
    public MemberResponse add(UUID tenantId, String email, ClubRole role) {
        User user = users.findByEmail(User.normalizeEmail(email))
                .orElseThrow(() -> new NotFoundException("No account with that email: they must register first"));
        if (memberships.findByUserIdAndTenantId(user.getId(), tenantId).isPresent()) {
            throw new ConflictException("Already a member of this club");
        }
        plans.requireMemberRoom(tenantId);
        Membership saved;
        try {
            saved = memberships.saveAndFlush(new Membership(user.getId(), tenantId, role));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Already a member of this club"); // concurrent add, caught by the unique key
        }
        audit.record("MEMBER_ADDED", "USER", user.getId(), Map.of("email", user.getEmail(), "role", role.name()));
        return toResponse(saved, user);
    }

    @Transactional
    public MemberResponse changeRole(UUID tenantId, UUID userId, ClubRole newRole) {
        Membership membership = find(tenantId, userId);
        ClubRole previous = membership.getRole();
        if (previous == ClubRole.CLUB_ADMIN && newRole != ClubRole.CLUB_ADMIN) {
            requireAnotherAdmin(tenantId);
        }
        membership.changeRole(newRole);
        audit.record("MEMBER_ROLE_CHANGED", "USER", userId, Map.of("from", previous.name(), "to", newRole.name()));
        return toResponse(membership, users.findById(userId).orElseThrow());
    }

    @Transactional
    public void remove(UUID tenantId, UUID userId) {
        Membership membership = find(tenantId, userId);
        if (membership.getRole() == ClubRole.CLUB_ADMIN) {
            requireAnotherAdmin(tenantId);
        }
        memberships.delete(membership);
        audit.record("MEMBER_REMOVED", "USER", userId, Map.of("role", membership.getRole().name()));
    }

    /** A club with zero admins could never be managed again: refuse to demote/remove the last one. */
    private void requireAnotherAdmin(UUID tenantId) {
        if (memberships.countByTenantIdAndRole(tenantId, ClubRole.CLUB_ADMIN) <= 1) {
            throw new ConflictException("A club must keep at least one CLUB_ADMIN");
        }
    }

    private Membership find(UUID tenantId, UUID userId) {
        return memberships.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new NotFoundException("Not a member of this club"));
    }

    private static MemberResponse toResponse(Membership m, User u) {
        return new MemberResponse(m.getUserId(), u.getEmail(), u.getFullName(), m.getRole(), m.getJoinedAt());
    }
}
