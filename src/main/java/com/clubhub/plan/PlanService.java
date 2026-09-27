package com.clubhub.plan;

import com.clubhub.common.NotFoundException;
import com.clubhub.event.EventRepository;
import com.clubhub.event.EventStatus;
import com.clubhub.membership.MembershipRepository;
import com.clubhub.plan.PlanExceptions.FeatureNotAvailableException;
import com.clubhub.plan.PlanExceptions.PlanLimitExceededException;
import com.clubhub.recruitment.DriveRepository;
import com.clubhub.recruitment.DriveStatus;
import com.clubhub.tenant.CurrentClub;
import com.clubhub.tenant.Tenant;
import com.clubhub.tenant.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Answers "may this club do that?" from its plan plus per-club feature overrides. */
@Service
public class PlanService {

    public enum Limit {
        MEMBERS("members"), UPCOMING_EVENTS("upcoming events"), OPEN_DRIVES("open recruitment drives");

        private final String label;

        Limit(String label) {
            this.label = label;
        }

        int max(Plan plan) {
            return switch (this) {
                case MEMBERS -> plan.maxMembers();
                case UPCOMING_EVENTS -> plan.maxUpcomingEvents();
                case OPEN_DRIVES -> plan.maxOpenDrives();
            };
        }
    }

    public record PlanView(Plan plan, Map<Limit, Integer> limits, Map<Limit, Long> usage,
                           Map<Feature, Boolean> features, int requestsPerMinute) {
    }

    private final TenantRepository tenants;
    private final TenantFeatureRepository overrides;
    private final CurrentClub currentClub;
    private final MembershipRepository memberships;
    private final EventRepository events;
    private final DriveRepository drives;

    public PlanService(TenantRepository tenants, TenantFeatureRepository overrides, CurrentClub currentClub,
                       MembershipRepository memberships, EventRepository events, DriveRepository drives) {
        this.tenants = tenants;
        this.overrides = overrides;
        this.currentClub = currentClub;
        this.memberships = memberships;
        this.events = events;
        this.drives = drives;
    }

    public boolean isEnabled(Tenant tenant, Feature feature) {
        return overrides.findById(new TenantFeature.Key(tenant.getId(), feature))
                .map(TenantFeature::isEnabled)
                .orElse(tenant.getPlan().includes(feature));
    }

    public boolean isEnabled(UUID tenantId, Feature feature) {
        return tenants.findById(tenantId).map(t -> isEnabled(t, feature)).orElse(false);
    }

    /** For code running inside a club request. */
    public void requireFeature(Feature feature) {
        Tenant club = currentClub.get();
        if (!isEnabled(club, feature)) {
            throw new FeatureNotAvailableException(club.getPlan(), feature);
        }
    }

    /** {@code used} is the count BEFORE the new item is added. */
    public void requireRoom(Tenant club, Limit limit, long used) {
        int max = limit.max(club.getPlan());
        if (used >= max) {
            throw new PlanLimitExceededException(club.getPlan(), limit.label, max);
        }
    }

    public void requireRoom(Limit limit, long used) {
        requireRoom(currentClub.get(), limit, used);
    }

    public void requireMemberRoom(UUID tenantId) {
        Tenant club = tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("Club not found"));
        requireRoom(club, Limit.MEMBERS, memberships.countByTenantId(tenantId));
    }

    @Transactional(readOnly = true)
    public PlanView view() {
        Tenant club = currentClub.get();
        Map<Limit, Integer> limits = new EnumMap<>(Limit.class);
        for (Limit limit : Limit.values()) {
            limits.put(limit, limit.max(club.getPlan()));
        }
        Map<Limit, Long> usage = new EnumMap<>(Limit.class);
        usage.put(Limit.MEMBERS, memberships.countByTenantId(club.getId()));
        usage.put(Limit.UPCOMING_EVENTS, events.countByEndsAtAfterAndStatusNot(Instant.now(), EventStatus.CANCELLED));
        usage.put(Limit.OPEN_DRIVES, drives.countByStatus(DriveStatus.OPEN));
        Map<Feature, Boolean> features = new EnumMap<>(Feature.class);
        for (Feature feature : Feature.values()) {
            features.put(feature, isEnabled(club, feature));
        }
        return new PlanView(club.getPlan(), limits, usage, features, club.getPlan().requestsPerMinute());
    }

    @Transactional
    public Tenant changePlan(UUID tenantId, Plan plan) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("Club not found"));
        tenant.changePlan(plan);
        return tenant;
    }

    @Transactional
    public void setFeature(UUID tenantId, Feature feature, boolean enabled) {
        tenants.findById(tenantId).orElseThrow(() -> new NotFoundException("Club not found"));
        overrides.findById(new TenantFeature.Key(tenantId, feature))
                .ifPresentOrElse(o -> o.set(enabled), () -> overrides.save(new TenantFeature(tenantId, feature, enabled)));
    }
}
