package com.clubhub.club;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ClubProfileDtos {

    private ClubProfileDtos() {
    }

    public record UpdateClubProfileRequest(
            @NotBlank @Size(max = 120) String displayName,
            @Size(max = 2000) String description,
            @Email @Size(max = 254) String contactEmail) {
    }

    public record ClubProfileResponse(String displayName, String description, String contactEmail, Instant updatedAt) {

        static ClubProfileResponse from(ClubProfile profile) {
            return new ClubProfileResponse(profile.getDisplayName(), profile.getDescription(),
                    profile.getContactEmail(), profile.getUpdatedAt());
        }
    }
}
