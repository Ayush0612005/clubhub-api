package com.clubhub.event;

import com.clubhub.common.ConflictException;
import com.clubhub.common.NotFoundException;
import com.clubhub.event.AttendanceDtos.AttendanceSummary;
import com.clubhub.event.AttendanceDtos.CheckInResponse;
import com.clubhub.event.TicketService.InvalidTicketException;
import com.clubhub.event.TicketService.Ticket;
import com.clubhub.tenancy.TenantContext;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Door check-in. A QR scan is accepted only if the ticket's signature is valid, it was issued by
 * THIS club for THIS event, the event's check-in window is open, the holder is still registered,
 * and they haven't been checked in yet (also enforced by a unique key in the database).
 */
@Service
public class AttendanceService {

    private final EventRepository events;
    private final EventRegistrationRepository registrations;
    private final AttendanceRepository attendance;
    private final UserRepository users;
    private final TicketService tickets;

    public AttendanceService(EventRepository events, EventRegistrationRepository registrations,
                             AttendanceRepository attendance, UserRepository users, TicketService tickets) {
        this.events = events;
        this.registrations = registrations;
        this.attendance = attendance;
        this.users = users;
        this.tickets = tickets;
    }

    @Transactional
    public CheckInResponse scan(Long eventId, String rawTicket, UUID scannerId) {
        Ticket ticket = tickets.verify(rawTicket);
        if (!ticket.schema().equals(TenantContext.currentSchema().orElseThrow())) {
            throw new InvalidTicketException("This ticket belongs to another club");
        }
        if (ticket.eventId() != eventId) {
            throw new InvalidTicketException("This ticket is for a different event");
        }
        Event event = openForCheckIn(eventId);
        if (!registrations.existsByEventIdAndUserId(event.getId(), ticket.userId())) {
            throw new ConflictException("The ticket holder is no longer registered");
        }
        return checkIn(eventId, userOf(ticket.userId()), AttendanceMethod.QR, scannerId);
    }

    /** Walk-ins without a ticket, marked by the core team. Registration is not required. */
    @Transactional
    public CheckInResponse manual(Long eventId, String email, UUID scannerId) {
        openForCheckIn(eventId);
        User user = users.findByEmail(User.normalizeEmail(email))
                .orElseThrow(() -> new NotFoundException("No account with that email"));
        return checkIn(eventId, user, AttendanceMethod.MANUAL, scannerId);
    }

    @Transactional(readOnly = true)
    public AttendanceSummary summary(Long eventId) {
        if (!events.existsById(eventId)) {
            throw new NotFoundException("Event not found");
        }
        List<Attendance> rows = attendance.findAllByEventIdOrderByCheckedInAtAsc(eventId);
        Map<UUID, User> byId = users.findAllById(rows.stream().map(Attendance::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<CheckInResponse> attendees = rows.stream().map(a -> response(a, byId.get(a.getUserId()))).toList();
        return new AttendanceSummary(eventId, registrations.countByEventId(eventId), rows.size(), attendees);
    }

    private Event openForCheckIn(Long eventId) {
        Event event = events.findById(eventId).orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.isCheckInOpen(Instant.now())) {
            throw new ConflictException("Check-in is not open for this event");
        }
        return event;
    }

    private CheckInResponse checkIn(Long eventId, User user, AttendanceMethod method, UUID scannerId) {
        if (attendance.existsByEventIdAndUserId(eventId, user.getId())) {
            throw new ConflictException(user.getFullName() + " is already checked in");
        }
        try {
            return response(attendance.saveAndFlush(new Attendance(eventId, user.getId(), method, scannerId)), user);
        } catch (DataIntegrityViolationException e) {
            // two scanners at two doors scanned the same ticket at the same moment
            throw new ConflictException(user.getFullName() + " is already checked in");
        }
    }

    private User userOf(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Ticket holder no longer exists"));
    }

    private static CheckInResponse response(Attendance a, User u) {
        return new CheckInResponse(a.getUserId(), u == null ? null : u.getEmail(), u == null ? null : u.getFullName(),
                a.getMethod(), a.getCheckedInAt());
    }
}
