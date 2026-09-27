package com.clubhub.tenant;

import com.clubhub.common.NotFoundException;
import com.clubhub.tenant.TenantDtos.CreateTenantRequest;
import com.clubhub.tenant.TenantDtos.TenantResponse;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Platform-level club management. PLATFORM_ADMIN only (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/platform/tenants")
public class PlatformTenantController {

    private final TenantProvisioningService provisioningService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public PlatformTenantController(TenantProvisioningService provisioningService, TenantRepository tenantRepository,
                                    UserRepository userRepository) {
        this.provisioningService = provisioningService;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody CreateTenantRequest request) {
        UUID ownerId = resolveOwner(request.ownerEmail(), jwt);
        Tenant tenant = provisioningService.provision(request.slug(), request.name(), ownerId);
        return ResponseEntity
                .created(URI.create("/api/platform/tenants/" + tenant.getId()))
                .body(TenantResponse.from(tenant));
    }

    @GetMapping
    public List<TenantResponse> list() {
        return tenantRepository.findAll(Sort.by("createdAt")).stream()
                .map(TenantResponse::from)
                .toList();
    }

    /** The named owner (must already have an account), or the calling admin if none is given. */
    private UUID resolveOwner(String ownerEmail, Jwt jwt) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            return UUID.fromString(jwt.getSubject());
        }
        return userRepository.findByEmail(User.normalizeEmail(ownerEmail))
                .map(User::getId)
                .orElseThrow(() -> new NotFoundException("No account with email " + User.normalizeEmail(ownerEmail)
                        + ": the club owner must register first"));
    }
}
