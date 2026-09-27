package com.clubhub.club;

import com.clubhub.club.ClubProfileDtos.ClubProfileResponse;
import com.clubhub.club.ClubProfileDtos.UpdateClubProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Club-scoped: the club comes from the caller's access token (enforced by TenantFilter on /api/club/**). */
@RestController
@RequestMapping("/api/club/profile")
public class ClubProfileController {

    private final ClubProfileService service;

    public ClubProfileController(ClubProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ClubProfileResponse get() {
        return service.get();
    }

    @PutMapping
    public ClubProfileResponse update(@Valid @RequestBody UpdateClubProfileRequest request) {
        return service.update(request);
    }
}
