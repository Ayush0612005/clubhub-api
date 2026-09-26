package com.clubhub.tenant;

import com.clubhub.tenant.TenantDtos.CreateTenantRequest;
import com.clubhub.tenant.TenantDtos.TenantResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Platform-level club management. UNSECURED until Phase 2, where it becomes PLATFORM_ADMIN only.
 */
@RestController
@RequestMapping("/api/platform/tenants")
public class PlatformTenantController {

    private final TenantProvisioningService provisioningService;
    private final TenantRepository tenantRepository;

    public PlatformTenantController(TenantProvisioningService provisioningService, TenantRepository tenantRepository) {
        this.provisioningService = provisioningService;
        this.tenantRepository = tenantRepository;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = provisioningService.provision(request.slug(), request.name());
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
}
