package com.clubhub.event;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class EventDtos {

    private EventDtos() {
    }

    public record CreateEventRequest(
            @NotBlank @Size(max = 150) String title,
            @Size(max = 5000) String description,
            @NotBlank @Size(max = 200) String venue,
            @NotNull @Future Instant startsAt,
            @NotNull Instant endsAt,
            @Positive Integer capacity,
            @NotNull EventVisibility visibility) {
    }

    public record EventResponse(Long id, String title, String description, String venue, Instant startsAt,
                                Instant endsAt, Integer capacity, EventVisibility visibility, EventStatus status,
                                long registeredCount) {

        static EventResponse from(Event e, long registeredCount) {
            return new EventResponse(e.getId(), e.getTitle(), e.getDescription(), e.getVenue(), e.getStartsAt(),
                    e.getEndsAt(), e.getCapacity(), e.getVisibility(), e.getStatus(), registeredCount);
        }
    }

    public record RegistrationResponse(Long eventId, UUID userId, Instant registeredAt) {

        static RegistrationResponse from(EventRegistration r) {
            return new RegistrationResponse(r.getEventId(), r.getUserId(), r.getRegisteredAt());
        }
    }

    /** The ticket string is what the QR code encodes; clients may render it themselves or fetch the PNG. */
    public record TicketResponse(Long eventId, String ticket) {
    }

    public record Registrant(UUID userId, String email, String fullName, Instant registeredAt, boolean attended) {
    }
}
