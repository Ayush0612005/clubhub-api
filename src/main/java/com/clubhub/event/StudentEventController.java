package com.clubhub.event;

import com.clubhub.event.EventDtos.EventResponse;
import com.clubhub.event.EventDtos.RegistrationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * A club's PUBLIC events for any logged-in student (ClubBySlugFilter binds the club from the slug).
 * Members-only events are invisible here, even to members: they use /api/club/events.
 */
@RestController
@RequestMapping("/api/clubs/{slug}/events")
public class StudentEventController {

    private final EventService service;

    public StudentEventController(EventService service) {
        this.service = service;
    }

    @GetMapping
    public List<EventResponse> upcoming() {
        return service.upcoming(false, false);
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable Long id) {
        return service.get(id, false, false);
    }

    @PostMapping("/{id}/registration")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.register(id, UUID.fromString(jwt.getSubject()), false);
    }

    @DeleteMapping("/{id}/registration")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        service.unregister(id, UUID.fromString(jwt.getSubject()), false);
    }
}
