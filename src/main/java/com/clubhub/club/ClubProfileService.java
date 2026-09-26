package com.clubhub.club;

import com.clubhub.club.ClubProfileDtos.ClubProfileResponse;
import com.clubhub.club.ClubProfileDtos.UpdateClubProfileRequest;
import com.clubhub.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain single-tenant looking code: no schema, no tenant id anywhere.
 * TenantFilter + Hibernate routing decide which club's row this touches.
 */
@Service
public class ClubProfileService {

    private final ClubProfileRepository repository;

    public ClubProfileService(ClubProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ClubProfileResponse get() {
        return ClubProfileResponse.from(load());
    }

    @Transactional
    public ClubProfileResponse update(UpdateClubProfileRequest request) {
        ClubProfile profile = load();
        profile.update(request.displayName(), request.description(), request.contactEmail());
        return ClubProfileResponse.from(profile); // dirty checking flushes on commit
    }

    private ClubProfile load() {
        return repository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException("Club profile not found"));
    }
}
