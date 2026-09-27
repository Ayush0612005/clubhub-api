package com.clubhub.membership;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class MemberDtos {

    private MemberDtos() {
    }

    public record AddMemberRequest(@NotBlank @Email String email, @NotNull ClubRole role) {
    }

    public record ChangeRoleRequest(@NotNull ClubRole role) {
    }

    public record MemberResponse(UUID userId, String email, String fullName, ClubRole role, Instant joinedAt) {
    }
}
