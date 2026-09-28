package com.clubhub.campus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CampusDtos {

    /** Links must be real web links: blocks javascript: and friends from ever reaching an href. */
    static final String URL = "^https?://\\S+$";

    private CampusDtos() {
    }

    public record ClubRef(String slug, String name, String category) {
    }

    /** onClubHub: the club claimed its listing and runs a ClubHub workspace (workspaceSlug). */
    public record ClubCard(String slug, String name, String category, String kind, String home, String description,
                           boolean onClubHub, long upcomingEvents, boolean recruiting) {
    }

    public record ClubDetail(String slug, String name, String category, String kind, String home, String description,
                             String officialUrl, String sourceUrl, String workspaceSlug,
                             List<EventView> events, List<RecruitmentView> recruitments) {
    }

    public record EventView(UUID id, String title, String description, Instant startsAt, Instant endsAt, String venue,
                            String registrationUrl, String sourceUrl, ClubRef club, CampusSource source,
                            ModerationStatus status) {
    }

    public record RecruitmentView(UUID id, String title, String description, String applyUrl, LocalDate deadline,
                                  ClubRef club, ModerationStatus status) {
    }

    /** clubSlug is optional: university and department events don't belong to a club. */
    public record EventRequest(
            @Size(max = 60) String clubSlug,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @NotNull Instant startsAt,
            Instant endsAt,
            @Size(max = 200) String venue,
            @Size(max = 500) @Pattern(regexp = URL, message = "must be an http(s) link") String registrationUrl,
            @Size(max = 500) @Pattern(regexp = URL, message = "must be an http(s) link") String sourceUrl) {
    }

    public record RecruitmentRequest(
            @NotBlank @Size(max = 60) String clubSlug,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @Size(max = 500) @Pattern(regexp = URL, message = "must be an http(s) link") String applyUrl,
            LocalDate deadline) {
    }

    public record PendingEvent(EventView event, String submittedBy) {
    }

    public record PendingRecruitment(RecruitmentView recruitment, String submittedBy) {
    }

    public record ModerationQueue(List<PendingEvent> events, List<PendingRecruitment> recruitments) {
    }

    public record ImportResult(int inFeed, int added, int skippedPast, int alreadyKnown) {
    }
}
