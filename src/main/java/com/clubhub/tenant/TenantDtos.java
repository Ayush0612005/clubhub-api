package com.clubhub.tenant;

import com.clubhub.plan.Plan;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Request/response shapes for the platform tenant API. Entities never leave the service layer. */
public final class TenantDtos {

    private TenantDtos() {
    }

    public record CreateTenantRequest(
            @NotBlank
            @Pattern(regexp = "^[a-z][a-z0-9_]{2,39}$",
                    message = "must be 3-40 chars: lowercase letters, digits or _, starting with a letter")
            String slug,

            @NotBlank
            @Size(max = 120)
            String name,

            // optional: the registered user who will run the club (becomes CLUB_ADMIN); defaults to the caller
            @Email
            String ownerEmail) {
    }

    public record TenantResponse(UUID id, String slug, String name, TenantStatus status, Plan plan,
                                 Instant createdAt) {

        public static TenantResponse from(Tenant tenant) {
            return new TenantResponse(tenant.getId(), tenant.getSlug(), tenant.getName(),
                    tenant.getStatus(), tenant.getPlan(), tenant.getCreatedAt());
        }
    }
}
