package com.clubhub.club;

import com.clubhub.audit.AuditService;
import com.clubhub.club.ClubProfileDtos.ClubProfileResponse;
import com.clubhub.club.ClubProfileDtos.UpdateClubProfileRequest;
import com.clubhub.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Plain single-tenant looking code: no schema, no tenant id anywhere.
 * TenantFilter + Hibernate routing decide which club's row this touches.
 */
@Service
public class ClubProfileService {

    private final ClubProfileRepository repository;
    private final AuditService audit;

    public ClubProfileService(ClubProfileRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ClubProfileResponse get() {
        return ClubProfileResponse.from(load());
    }

    @Transactional
    public ClubProfileResponse update(UpdateClubProfileRequest request) {
        ClubProfile profile = load();
        profile.update(request.displayName(), request.description(), request.contactEmail());
        audit.record("CLUB_PROFILE_UPDATED", "CLUB_PROFILE", profile.getId(),
                Map.of("displayName", request.displayName()));
        return ClubProfileResponse.from(profile); // dirty checking flushes on commit
    }

    private ClubProfile load() {
        return repository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException("Club profile not found"));
    }
}
