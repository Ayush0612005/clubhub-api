package com.clubhub.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            // BCrypt only uses the first 72 bytes of a password; reject longer ones instead of silently truncating
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 120) String fullName) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record UserResponse(UUID id, String email, String fullName) {
    }

    /** clubSlug (optional): keep the new access token scoped to this club, re-checking membership. */
    public record RefreshRequest(@NotBlank String refreshToken, String clubSlug) {
    }

    public record SwitchClubRequest(@NotBlank String clubSlug) {
    }

    public record ActiveClub(String slug, String role) {
    }

    /** club is null when the access token is not scoped to any club. */
    public record TokenResponse(String accessToken, String tokenType, Instant expiresAt,
                                String refreshToken, Instant refreshExpiresAt, ActiveClub club) {

        static TokenResponse bearer(String accessToken, Instant expiresAt,
                                    String refreshToken, Instant refreshExpiresAt, ActiveClub club) {
            return new TokenResponse(accessToken, "Bearer", expiresAt, refreshToken, refreshExpiresAt, club);
        }
    }

    /** switch-club only replaces the access token; the refresh token is untouched. */
    public record ClubTokenResponse(String accessToken, String tokenType, Instant expiresAt, ActiveClub club) {
    }
}
