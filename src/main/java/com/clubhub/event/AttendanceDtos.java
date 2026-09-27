package com.clubhub.event;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    public record ScanRequest(@NotBlank @Size(max = 300) String ticket) {
    }

    public record ManualCheckInRequest(@NotBlank @Email String email) {
    }

    /** What the door scanner shows after a successful scan. */
    public record CheckInResponse(UUID userId, String email, String fullName, AttendanceMethod method,
                                  Instant checkedInAt) {
    }

    public record AttendanceSummary(Long eventId, long registered, long attended, List<CheckInResponse> attendees) {
    }
}
