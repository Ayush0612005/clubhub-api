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

    public record TokenResponse(String accessToken, String tokenType, Instant expiresAt) {

        static TokenResponse bearer(String token, Instant expiresAt) {
            return new TokenResponse(token, "Bearer", expiresAt);
        }
    }
}
