package com.clubhub.campus;

import com.clubhub.campus.CampusDtos.ClubCard;
import com.clubhub.campus.CampusDtos.ClubDetail;
import com.clubhub.campus.CampusDtos.EventRequest;
import com.clubhub.campus.CampusDtos.EventView;
import com.clubhub.campus.CampusDtos.RecruitmentRequest;
import com.clubhub.campus.CampusDtos.RecruitmentView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** What every signed-in student sees: all clubs, what's on, who's recruiting. Plus "suggest one". */
@RestController
@RequestMapping("/api/campus")
public class CampusController {

    private final CampusService campus;

    public CampusController(CampusService campus) {
        this.campus = campus;
    }

    @GetMapping("/clubs")
    public List<ClubCard> clubs() {
        return campus.clubs();
    }

    @GetMapping("/clubs/{slug}")
    public ClubDetail club(@PathVariable String slug) {
        return campus.club(slug);
    }

    @GetMapping("/events")
    public List<EventView> events() {
        return campus.upcomingEvents();
    }

    @GetMapping("/recruitments")
    public List<RecruitmentView> recruitments() {
        return campus.openRecruitments();
    }

    /** Goes to the moderation queue; shown to students only once a platform admin approves it. */
    @PostMapping("/suggestions/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventView suggestEvent(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EventRequest request) {
        return campus.suggestEvent(UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/suggestions/recruitments")
    @ResponseStatus(HttpStatus.CREATED)
    public RecruitmentView suggestRecruitment(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody RecruitmentRequest request) {
        return campus.suggestRecruitment(UUID.fromString(jwt.getSubject()), request);
    }
}
