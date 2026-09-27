package com.clubhub.event;

import com.clubhub.event.EventDtos.CreateEventRequest;
import com.clubhub.event.EventDtos.EventResponse;
import com.clubhub.event.EventDtos.Registrant;
import com.clubhub.event.EventDtos.RegistrationResponse;
import com.clubhub.event.EventDtos.TicketResponse;
import com.clubhub.membership.ClubRole;
import com.clubhub.tenancy.CurrentMember;
import com.clubhub.tenancy.CurrentMember.ClubMember;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Events of the caller's active club. Members see and join events; CORE+ create and run them. */
@RestController
@RequestMapping("/api/club/events")
public class ClubEventController {

    private final EventService service;

    public ClubEventController(EventService service) {
        this.service = service;
    }

    @GetMapping
    public List<EventResponse> upcoming() {
        return service.upcoming(true, isCore());
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable Long id) {
        return service.get(id, true, isCore());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@clubAuthz.atLeast('CORE')")
    public EventResponse create(@Valid @RequestBody CreateEventRequest request) {
        return service.create(request, member().userId());
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@clubAuthz.atLeast('CORE')")
    public EventResponse publish(@PathVariable Long id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@clubAuthz.atLeast('CORE')")
    public EventResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @GetMapping("/{id}/registrations")
    @PreAuthorize("@clubAuthz.atLeast('CORE')")
    public List<Registrant> registrants(@PathVariable Long id) {
        return service.registrants(id);
    }

    @PostMapping("/{id}/registration")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@PathVariable Long id) {
        return service.register(id, member().userId(), true);
    }

    @GetMapping("/{id}/ticket")
    public TicketResponse ticket(@PathVariable Long id) {
        return service.ticket(id, member().userId(), true);
    }

    @GetMapping("/{id}/ticket/qr")
    public ResponseEntity<byte[]> ticketQr(@PathVariable Long id) {
        return TicketImages.png(service.ticket(id, member().userId(), true));
    }

    @DeleteMapping("/{id}/registration")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@PathVariable Long id) {
        service.unregister(id, member().userId(), true);
    }

    private static boolean isCore() {
        return member().role().atLeast(ClubRole.CORE);
    }

    private static ClubMember member() {
        return CurrentMember.get().orElseThrow(); // always bound: TenantFilter guards /api/club/**
    }
}
