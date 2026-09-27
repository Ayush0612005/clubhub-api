package com.clubhub.event;

import com.clubhub.common.ConflictException;
import com.clubhub.common.NotFoundException;
import com.clubhub.event.EventDtos.CreateEventRequest;
import com.clubhub.event.EventDtos.EventResponse;
import com.clubhub.event.EventDtos.Registrant;
import com.clubhub.event.EventDtos.RegistrationResponse;
import com.clubhub.event.EventDtos.TicketResponse;
import com.clubhub.notification.DomainEvent;
import com.clubhub.notification.DomainEventPublisher;
import com.clubhub.tenancy.TenantContext;
import com.clubhub.tenant.CurrentClub;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Events of the club bound in TenantContext. Used by both entrances:
 * members via /api/club/events (member = true) and outsiders via /api/clubs/{slug}/events (member = false).
 */
@Service
public class EventService {

    private final EventRepository events;
    private final EventRegistrationRepository registrations;
    private final AttendanceRepository attendance;
    private final UserRepository users;
    private final TicketService tickets;
    private final DomainEventPublisher domainEvents;
    private final CurrentClub currentClub;

    public EventService(EventRepository events, EventRegistrationRepository registrations,
                        AttendanceRepository attendance, UserRepository users, TicketService tickets,
                        DomainEventPublisher domainEvents, CurrentClub currentClub) {
        this.events = events;
        this.registrations = registrations;
        this.attendance = attendance;
        this.users = users;
        this.tickets = tickets;
        this.domainEvents = domainEvents;
        this.currentClub = currentClub;
    }

    /** The caller's personal signed ticket; only registered attendees of a live event get one. */
    @Transactional(readOnly = true)
    public TicketResponse ticket(Long eventId, UUID userId, boolean member) {
        Event event = visible(eventId, member, false);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("This event was cancelled");
        }
        if (!registrations.existsByEventIdAndUserId(eventId, userId)) {
            throw new NotFoundException("You are not registered for this event");
        }
        String schema = TenantContext.currentSchema().orElseThrow();
        return new TicketResponse(eventId, tickets.issue(schema, eventId, userId));
    }

    @Transactional
    public EventResponse create(CreateEventRequest r, UUID createdBy) {
        Event event = events.save(new Event(r.title().strip(), r.description(), r.venue().strip(),
                r.startsAt(), r.endsAt(), r.capacity(), r.visibility(), createdBy));
        return EventResponse.from(event, 0);
    }

    /**
     * Upcoming (not yet finished) events the caller may see. Registration counts come from one
     * grouped query for the whole list instead of one COUNT per event.
     */
    @Transactional(readOnly = true)
    public List<EventResponse> upcoming(boolean member, boolean includeDrafts) {
        List<Event> visible = events.findAllByEndsAtAfterOrderByStartsAtAsc(Instant.now()).stream()
                .filter(e -> e.isVisibleTo(member) || (includeDrafts && e.getStatus() == EventStatus.DRAFT))
                .toList();
        if (visible.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = registrations.countByEventIds(visible.stream().map(Event::getId).toList()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return visible.stream().map(e -> EventResponse.from(e, counts.getOrDefault(e.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long eventId, boolean member, boolean includeDrafts) {
        Event event = visible(eventId, member, includeDrafts);
        return EventResponse.from(event, registrations.countByEventId(eventId));
    }

    @Transactional
    public EventResponse publish(Long eventId) {
        Event event = find(eventId);
        try {
            event.publish();
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        // the notification consumer fans this out to every member of the club
        domainEvents.publish(new DomainEvent.EventPublished(UUID.randomUUID(), DomainEvent.Club.of(currentClub.get()),
                Instant.now(), event.getId(), event.getTitle(), event.getVenue(), event.getStartsAt()));
        return EventResponse.from(event, registrations.countByEventId(eventId));
    }

    @Transactional
    public EventResponse cancel(Long eventId) {
        Event event = find(eventId);
        try {
            event.cancel();
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
        return EventResponse.from(event, registrations.countByEventId(eventId));
    }

    /**
     * Locks the event row first (SELECT ... FOR UPDATE): two students racing for the last seat are
     * serialized, so the capacity check and the insert behave as one atomic step.
     */
    @Transactional
    public RegistrationResponse register(Long eventId, UUID userId, boolean member) {
        Event event = events.findForUpdateById(eventId)
                .filter(e -> e.isVisibleTo(member))
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.isOpenForRegistration(Instant.now())) {
            throw new ConflictException("Registration for this event is closed");
        }
        if (registrations.existsByEventIdAndUserId(eventId, userId)) {
            throw new ConflictException("You are already registered for this event");
        }
        if (event.getCapacity() != null && registrations.countByEventId(eventId) >= event.getCapacity()) {
            throw new ConflictException("This event is full");
        }
        try {
            return RegistrationResponse.from(registrations.saveAndFlush(new EventRegistration(eventId, userId)));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("You are already registered for this event");
        }
    }

    @Transactional
    public void unregister(Long eventId, UUID userId, boolean member) {
        Event event = visible(eventId, member, false);
        if (!event.isOpenForRegistration(Instant.now())) {
            throw new ConflictException("The event has already started");
        }
        EventRegistration registration = registrations.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("You are not registered for this event"));
        registrations.delete(registration);
    }

    @Transactional(readOnly = true)
    public List<Registrant> registrants(Long eventId) {
        find(eventId);
        List<EventRegistration> all = registrations.findAllByEventIdOrderByRegisteredAtAsc(eventId);
        Map<UUID, User> byId = users.findAllById(all.stream().map(EventRegistration::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<UUID> attended = attendance.findAllByEventIdOrderByCheckedInAtAsc(eventId).stream()
                .map(Attendance::getUserId).collect(Collectors.toSet());
        return all.stream().map(r -> {
            User u = byId.get(r.getUserId());
            return new Registrant(r.getUserId(), u == null ? null : u.getEmail(), u == null ? null : u.getFullName(),
                    r.getRegisteredAt(), attended.contains(r.getUserId()));
        }).toList();
    }

    private Event find(Long eventId) {
        return events.findById(eventId).orElseThrow(() -> new NotFoundException("Event not found"));
    }

    /** Drafts and members-only events simply don't exist for callers who may not see them. */
    private Event visible(Long eventId, boolean member, boolean includeDrafts) {
        return events.findById(eventId)
                .filter(e -> e.isVisibleTo(member) || (includeDrafts && e.getStatus() == EventStatus.DRAFT))
                .orElseThrow(() -> new NotFoundException("Event not found"));
    }
}
