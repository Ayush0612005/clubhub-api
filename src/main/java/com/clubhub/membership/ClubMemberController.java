package com.clubhub.membership;

import com.clubhub.membership.MemberDtos.AddMemberRequest;
import com.clubhub.membership.MemberDtos.ChangeRoleRequest;
import com.clubhub.membership.MemberDtos.MemberResponse;
import com.clubhub.tenancy.CurrentMember;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Members of the caller's active club. Reading: any member. Changes: CLUB_ADMIN only. */
@RestController
@RequestMapping("/api/club/members")
public class ClubMemberController {

    private final MembershipService service;

    public ClubMemberController(MembershipService service) {
        this.service = service;
    }

    @GetMapping
    public List<MemberResponse> list() {
        return service.list(currentClubId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@clubAuthz.atLeast('CLUB_ADMIN')")
    public MemberResponse add(@Valid @RequestBody AddMemberRequest request) {
        return service.add(currentClubId(), request.email(), request.role());
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("@clubAuthz.atLeast('CLUB_ADMIN')")
    public MemberResponse changeRole(@PathVariable UUID userId, @Valid @RequestBody ChangeRoleRequest request) {
        return service.changeRole(currentClubId(), userId, request.role());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@clubAuthz.atLeast('CLUB_ADMIN')")
    public void remove(@PathVariable UUID userId) {
        service.remove(currentClubId(), userId);
    }

    private static UUID currentClubId() {
        return CurrentMember.get().orElseThrow().tenantId(); // always bound: TenantFilter guards /api/club/**
    }
}
