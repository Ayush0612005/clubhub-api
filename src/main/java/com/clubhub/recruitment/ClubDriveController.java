package com.clubhub.recruitment;

import com.clubhub.membership.ClubRole;
import com.clubhub.recruitment.DriveDtos.ChangeDriveStatusRequest;
import com.clubhub.recruitment.DriveDtos.CreateDriveRequest;
import com.clubhub.recruitment.DriveDtos.DriveResponse;
import com.clubhub.recruitment.DriveDtos.DriveSummary;
import com.clubhub.tenancy.CurrentMember;
import com.clubhub.tenancy.CurrentMember.ClubMember;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Recruitment drives of the caller's active club. Reading: any member. Managing: CORE and above. */
@RestController
@RequestMapping("/api/club/recruitment/drives")
public class ClubDriveController {

    private final DriveService service;

    public ClubDriveController(DriveService service) {
        this.service = service;
    }

    @GetMapping
    public List<DriveSummary> list() {
        return service.list(isCore());
    }

    @GetMapping("/{id}")
    public DriveResponse get(@PathVariable Long id) {
        return service.get(id, isCore());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@clubAuthz.atLeast('CORE')")
    public DriveResponse create(@Valid @RequestBody CreateDriveRequest request) {
        return service.create(request, member().userId());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@clubAuthz.atLeast('CORE')")
    public DriveResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeDriveStatusRequest request) {
        return service.changeStatus(id, request.status());
    }

    private static boolean isCore() {
        return member().role().atLeast(ClubRole.CORE);
    }

    private static ClubMember member() {
        return CurrentMember.get().orElseThrow(); // always bound: TenantFilter guards /api/club/**
    }
}
