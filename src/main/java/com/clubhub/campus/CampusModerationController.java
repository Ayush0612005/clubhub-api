package com.clubhub.campus;

import com.clubhub.campus.CampusDtos.EventRequest;
import com.clubhub.campus.CampusDtos.EventView;
import com.clubhub.campus.CampusDtos.ImportResult;
import com.clubhub.campus.CampusDtos.ModerationQueue;
import com.clubhub.campus.CampusDtos.RecruitmentRequest;
import com.clubhub.campus.CampusDtos.RecruitmentView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Platform team: review suggestions and imports, add or fix events and recruitments. PLATFORM_ADMIN only. */
@RestController
@RequestMapping("/api/platform/campus")
public class CampusModerationController {

    private final CampusService campus;
    private final SrmEventsImporter importer;

    public CampusModerationController(CampusService campus, SrmEventsImporter importer) {
        this.campus = campus;
        this.importer = importer;
    }

    @GetMapping("/queue")
    public ModerationQueue queue() {
        return campus.queue();
    }

    /** Server-side fetch. srmist.edu.in's bot protection may refuse it (HTTP 403); then use the paste route. */
    @PostMapping("/import/srm")
    public ImportResult importSrmEvents() {
        return importer.importNow();
    }

    /**
     * The admin opens the feed in their own browser (view-source:https://www.srmist.edu.in/events/feed/),
     * copies it and pastes it here. Same parser, same review queue; no bot protection is bypassed.
     */
    @PostMapping("/import/srm-paste")
    public ImportResult importPastedSrmFeed(@Valid @RequestBody PastedFeed feed) {
        return importer.importFeed(feed.xml());
    }

    public record PastedFeed(@jakarta.validation.constraints.NotBlank
                             @jakarta.validation.constraints.Size(max = 2_000_000) String xml) {
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventView createEvent(@Valid @RequestBody EventRequest request) {
        return campus.createEvent(request);
    }

    @PutMapping("/events/{id}")
    public EventView updateEvent(@PathVariable UUID id, @Valid @RequestBody EventRequest request) {
        return campus.updateEvent(id, request);
    }

    @PostMapping("/events/{id}/approve")
    public EventView approveEvent(@PathVariable UUID id) {
        return campus.reviewEvent(id, ModerationStatus.APPROVED);
    }

    @PostMapping("/events/{id}/reject")
    public EventView rejectEvent(@PathVariable UUID id) {
        return campus.reviewEvent(id, ModerationStatus.REJECTED);
    }

    @DeleteMapping("/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable UUID id) {
        campus.deleteEvent(id);
    }

    @PostMapping("/recruitments")
    @ResponseStatus(HttpStatus.CREATED)
    public RecruitmentView createRecruitment(@Valid @RequestBody RecruitmentRequest request) {
        return campus.createRecruitment(request);
    }

    @PutMapping("/recruitments/{id}")
    public RecruitmentView updateRecruitment(@PathVariable UUID id, @Valid @RequestBody RecruitmentRequest request) {
        return campus.updateRecruitment(id, request);
    }

    @PostMapping("/recruitments/{id}/approve")
    public RecruitmentView approveRecruitment(@PathVariable UUID id) {
        return campus.reviewRecruitment(id, ModerationStatus.APPROVED);
    }

    @PostMapping("/recruitments/{id}/reject")
    public RecruitmentView rejectRecruitment(@PathVariable UUID id) {
        return campus.reviewRecruitment(id, ModerationStatus.REJECTED);
    }

    @DeleteMapping("/recruitments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecruitment(@PathVariable UUID id) {
        campus.deleteRecruitment(id);
    }
}
