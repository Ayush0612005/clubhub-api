package com.clubhub.plan;

import com.clubhub.plan.PlanService.PlanView;
import com.clubhub.tenant.TenantDtos.TenantResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Club members read their plan and usage; the platform admin changes plans and feature overrides. */
@RestController
public class PlanControllers {

    public record ChangePlanRequest(@NotNull Plan plan) {
    }

    public record FeatureRequest(@NotNull Boolean enabled) {
    }

    private final PlanService plans;

    public PlanControllers(PlanService plans) {
        this.plans = plans;
    }

    @GetMapping("/api/club/plan")
    public PlanView clubPlan() {
        return plans.view();
    }

    /** PLATFORM_ADMIN only: /api/platform/** is restricted in SecurityConfig. */
    @PatchMapping("/api/platform/tenants/{tenantId}/plan")
    public TenantResponse changePlan(@PathVariable UUID tenantId, @Valid @RequestBody ChangePlanRequest request) {
        return TenantResponse.from(plans.changePlan(tenantId, request.plan()));
    }

    @PutMapping("/api/platform/tenants/{tenantId}/features/{feature}")
    public ResponseEntity<Void> setFeature(@PathVariable UUID tenantId, @PathVariable Feature feature,
                                           @Valid @RequestBody FeatureRequest request) {
        plans.setFeature(tenantId, feature, request.enabled());
        return ResponseEntity.noContent().build();
    }
}
