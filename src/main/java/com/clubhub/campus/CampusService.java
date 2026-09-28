package com.clubhub.campus;

import com.clubhub.campus.CampusDtos.ClubCard;
import com.clubhub.campus.CampusDtos.ClubDetail;
import com.clubhub.campus.CampusDtos.ClubRef;
import com.clubhub.campus.CampusDtos.EventRequest;
import com.clubhub.campus.CampusDtos.EventView;
import com.clubhub.campus.CampusDtos.ModerationQueue;
import com.clubhub.campus.CampusDtos.PendingEvent;
import com.clubhub.campus.CampusDtos.PendingRecruitment;
import com.clubhub.campus.CampusDtos.RecruitmentRequest;
import com.clubhub.campus.CampusDtos.RecruitmentView;
import com.clubhub.common.NotFoundException;
import com.clubhub.tenant.Tenant;
import com.clubhub.tenant.TenantRepository;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The campus directory: every club, every approved event and open recruitment, in one feed.
 * Students read it and suggest additions; the platform team moderates. Nothing needs a club owner.
 */
@Service
public class CampusService {

    static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Kolkata");
    /** An event without an end time stays listed this long after it starts. */
    static final Duration GRACE = Duration.ofHours(12);
    /** Spam guard: a student can have at most this many suggestions waiting for review. */
    static final int MAX_PENDING_PER_STUDENT = 5;

    private final ClubListingRepository listings;
    private final CampusEventRepository events;
    private final CampusRecruitmentRepository recruitments;
    private final TenantRepository tenants;
    private final UserRepository users;

    public CampusService(ClubListingRepository listings, CampusEventRepository events,
                         CampusRecruitmentRepository recruitments, TenantRepository tenants, UserRepository users) {
        this.listings = listings;
        this.events = events;
        this.recruitments = recruitments;
        this.tenants = tenants;
        this.users = users;
    }

    // ---- student reads (approved content only)

    @Transactional(readOnly = true)
    public List<ClubCard> clubs() {
        Map<UUID, Long> eventCounts = events.findUpcoming(upcomingCutoff()).stream()
                .filter(e -> e.getClubListingId() != null)
                .collect(Collectors.groupingBy(CampusEvent::getClubListingId, Collectors.counting()));
        Set<UUID> recruiting = recruitments.findOpen(today()).stream()
                .map(CampusRecruitment::getClubListingId)
                .collect(Collectors.toSet());
        Set<UUID> onClubHub = activeWorkspaces(listings.findAll()).keySet();

        return listings.findAllByOrderByNameAsc().stream()
                .map(l -> new ClubCard(l.getSlug(), l.getName(), l.getCategory(), l.getKind(), l.getHome(),
                        l.getDescription(), onClubHub.contains(l.getId()), eventCounts.getOrDefault(l.getId(), 0L),
                        recruiting.contains(l.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClubDetail club(String slug) {
        ClubListing l = listing(slug);
        Map<UUID, ClubListing> byId = Map.of(l.getId(), l);
        String workspace = activeWorkspaces(List.of(l)).get(l.getId());
        return new ClubDetail(l.getSlug(), l.getName(), l.getCategory(), l.getKind(), l.getHome(), l.getDescription(),
                l.getOfficialUrl(), l.getSourceUrl(), workspace,
                events.findUpcomingForClub(upcomingCutoff(), l.getId()).stream().map(e -> view(e, byId)).toList(),
                recruitments.findOpenForClub(today(), l.getId()).stream().map(r -> view(r, byId)).toList());
    }

    @Transactional(readOnly = true)
    public List<EventView> upcomingEvents() {
        Map<UUID, ClubListing> byId = allListingsById();
        return events.findUpcoming(upcomingCutoff()).stream().map(e -> view(e, byId)).toList();
    }

    @Transactional(readOnly = true)
    public List<RecruitmentView> openRecruitments() {
        Map<UUID, ClubListing> byId = allListingsById();
        return recruitments.findOpen(today()).stream().map(r -> view(r, byId)).toList();
    }

    // ---- student suggestions (land in the moderation queue)

    @Transactional
    public EventView suggestEvent(UUID userId, EventRequest request) {
        requireRoomInQueue(events.countBySubmittedByAndStatus(userId, ModerationStatus.PENDING));
        CampusEvent event = new CampusEvent(request.title().trim(), CampusSource.STUDENT, ModerationStatus.PENDING);
        apply(event, request);
        event.submittedBy(userId);
        return view(events.save(event), allListingsById());
    }

    @Transactional
    public RecruitmentView suggestRecruitment(UUID userId, RecruitmentRequest request) {
        requireRoomInQueue(recruitments.countBySubmittedByAndStatus(userId, ModerationStatus.PENDING));
        CampusRecruitment recruitment = new CampusRecruitment(listing(request.clubSlug()).getId(),
                request.title().trim(), CampusSource.STUDENT, ModerationStatus.PENDING);
        apply(recruitment, request);
        recruitment.submittedBy(userId);
        return view(recruitments.save(recruitment), allListingsById());
    }

    // ---- moderation (platform admins)

    @Transactional(readOnly = true)
    public ModerationQueue queue() {
        Map<UUID, ClubListing> byId = allListingsById();
        List<CampusEvent> pendingEvents = events.findAllByStatusOrderByCreatedAtAsc(ModerationStatus.PENDING);
        List<CampusRecruitment> pendingRecruitments =
                recruitments.findAllByStatusOrderByCreatedAtAsc(ModerationStatus.PENDING);
        Map<UUID, String> submitters = submitterLabels(
                pendingEvents.stream().map(CampusEvent::getSubmittedBy),
                pendingRecruitments.stream().map(CampusRecruitment::getSubmittedBy));
        return new ModerationQueue(
                pendingEvents.stream().map(e -> new PendingEvent(view(e, byId), submitterOf(e.getSubmittedBy(),
                        e.getSource(), submitters))).toList(),
                pendingRecruitments.stream().map(r -> new PendingRecruitment(view(r, byId),
                        submitterOf(r.getSubmittedBy(), r.getSource(), submitters))).toList());
    }

    @Transactional
    public EventView createEvent(EventRequest request) {
        CampusEvent event = new CampusEvent(request.title().trim(), CampusSource.ADMIN, ModerationStatus.APPROVED);
        apply(event, request);
        return view(events.save(event), allListingsById());
    }

    @Transactional
    public EventView updateEvent(UUID id, EventRequest request) {
        CampusEvent event = events.findById(id).orElseThrow(() -> new NotFoundException("Event not found"));
        apply(event, request);
        return view(event, allListingsById());
    }

    @Transactional
    public EventView reviewEvent(UUID id, ModerationStatus decision) {
        CampusEvent event = events.findById(id).orElseThrow(() -> new NotFoundException("Event not found"));
        event.review(decision);
        return view(event, allListingsById());
    }

    @Transactional
    public void deleteEvent(UUID id) {
        events.deleteById(id);
    }

    @Transactional
    public RecruitmentView createRecruitment(RecruitmentRequest request) {
        CampusRecruitment recruitment = new CampusRecruitment(listing(request.clubSlug()).getId(),
                request.title().trim(), CampusSource.ADMIN, ModerationStatus.APPROVED);
        apply(recruitment, request);
        return view(recruitments.save(recruitment), allListingsById());
    }

    @Transactional
    public RecruitmentView updateRecruitment(UUID id, RecruitmentRequest request) {
        CampusRecruitment recruitment = recruitments.findById(id)
                .orElseThrow(() -> new NotFoundException("Recruitment not found"));
        apply(recruitment, request);
        return view(recruitment, allListingsById());
    }

    @Transactional
    public RecruitmentView reviewRecruitment(UUID id, ModerationStatus decision) {
        CampusRecruitment recruitment = recruitments.findById(id)
                .orElseThrow(() -> new NotFoundException("Recruitment not found"));
        recruitment.review(decision);
        return view(recruitment, allListingsById());
    }

    @Transactional
    public void deleteRecruitment(UUID id) {
        recruitments.deleteById(id);
    }

    // ---- helpers

    private void apply(CampusEvent event, EventRequest r) {
        if (r.endsAt() != null && r.endsAt().isBefore(r.startsAt())) {
            throw new IllegalArgumentException("The event can't end before it starts");
        }
        UUID clubId = r.clubSlug() == null || r.clubSlug().isBlank() ? null : listing(r.clubSlug()).getId();
        event.edit(clubId, r.title().trim(), blankToNull(r.description()), r.startsAt(), r.endsAt(),
                blankToNull(r.venue()), blankToNull(r.registrationUrl()), blankToNull(r.sourceUrl()));
    }

    private void apply(CampusRecruitment recruitment, RecruitmentRequest r) {
        recruitment.edit(listing(r.clubSlug()).getId(), r.title().trim(), blankToNull(r.description()),
                blankToNull(r.applyUrl()), r.deadline());
    }

    private static void requireRoomInQueue(long pending) {
        if (pending >= MAX_PENDING_PER_STUDENT) {
            throw new IllegalArgumentException(
                    "You already have " + MAX_PENDING_PER_STUDENT + " suggestions waiting for review. Try again once they're checked.");
        }
    }

    private ClubListing listing(String slug) {
        return listings.findBySlug(slug).orElseThrow(() -> new NotFoundException("Club not found: " + slug));
    }

    private Map<UUID, ClubListing> allListingsById() {
        return listings.findAll().stream().collect(Collectors.toMap(ClubListing::getId, Function.identity()));
    }

    /** listing id -> workspace slug, for listings whose club runs an active ClubHub workspace. */
    private Map<UUID, String> activeWorkspaces(Collection<ClubListing> candidates) {
        Map<UUID, UUID> tenantToListing = candidates.stream().filter(l -> l.getTenantId() != null)
                .collect(Collectors.toMap(ClubListing::getTenantId, ClubListing::getId));
        return tenants.findAllById(tenantToListing.keySet()).stream()
                .filter(Tenant::isActive)
                .collect(Collectors.toMap(t -> tenantToListing.get(t.getId()), Tenant::getSlug));
    }

    @SafeVarargs
    private Map<UUID, String> submitterLabels(java.util.stream.Stream<UUID>... ids) {
        Set<UUID> all = java.util.Arrays.stream(ids).flatMap(Function.identity())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return users.findAllById(all).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getFullName() + " <" + u.getEmail() + ">"));
    }

    private static String submitterOf(UUID userId, CampusSource source, Map<UUID, String> labels) {
        if (source == CampusSource.SRM_FEED) {
            return "SRM events feed";
        }
        return userId == null ? "Platform team" : labels.getOrDefault(userId, "Deleted account");
    }

    private static EventView view(CampusEvent e, Map<UUID, ClubListing> byId) {
        return new EventView(e.getId(), e.getTitle(), e.getDescription(), e.getStartsAt(), e.getEndsAt(), e.getVenue(),
                e.getRegistrationUrl(), e.getSourceUrl(), ref(byId.get(e.getClubListingId())), e.getSource(),
                e.getStatus());
    }

    private static RecruitmentView view(CampusRecruitment r, Map<UUID, ClubListing> byId) {
        return new RecruitmentView(r.getId(), r.getTitle(), r.getDescription(), r.getApplyUrl(), r.getDeadline(),
                ref(byId.get(r.getClubListingId())), r.getStatus());
    }

    private static ClubRef ref(ClubListing l) {
        return l == null ? null : new ClubRef(l.getSlug(), l.getName(), l.getCategory());
    }

    private static Instant upcomingCutoff() {
        return Instant.now().minus(GRACE);
    }

    private static LocalDate today() {
        return LocalDate.now(CAMPUS_ZONE);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
