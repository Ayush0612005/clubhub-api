package com.clubhub.event;

import com.clubhub.event.AttendanceDtos.AttendanceSummary;
import com.clubhub.event.AttendanceDtos.CheckInResponse;
import com.clubhub.event.AttendanceDtos.ManualCheckInRequest;
import com.clubhub.event.AttendanceDtos.ScanRequest;
import com.clubhub.tenancy.CurrentMember;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Running the door at an event: CORE and above. */
@RestController
@RequestMapping("/api/club/events/{eventId}")
@PreAuthorize("@clubAuthz.atLeast('CORE')")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @PostMapping("/check-ins")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckInResponse scan(@PathVariable Long eventId, @Valid @RequestBody ScanRequest request) {
        return service.scan(eventId, request.ticket(), scannerId());
    }

    @PostMapping("/check-ins/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckInResponse manual(@PathVariable Long eventId, @Valid @RequestBody ManualCheckInRequest request) {
        return service.manual(eventId, request.email(), scannerId());
    }

    @GetMapping("/attendance")
    public AttendanceSummary attendance(@PathVariable Long eventId) {
        return service.summary(eventId);
    }

    private static UUID scannerId() {
        return CurrentMember.get().orElseThrow().userId(); // bound by TenantFilter
    }
}
