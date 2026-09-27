package com.clubhub.recruitment;

import com.clubhub.recruitment.ApplicationDtos.ApplyRequest;
import com.clubhub.recruitment.ApplicationDtos.MyApplicationResponse;
import com.clubhub.recruitment.DriveDtos.DriveResponse;
import com.clubhub.recruitment.DriveDtos.DriveSummary;
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

/**
 * Student-facing recruitment of one club, for any logged-in user (membership not required).
 * The club comes from the URL slug (ClubBySlugFilter binds its schema before this runs).
 */
@RestController
@RequestMapping("/api/clubs/{slug}/recruitment")
public class StudentRecruitmentController {

    private final StudentApplicationService service;

    public StudentRecruitmentController(StudentApplicationService service) {
        this.service = service;
    }

    @GetMapping("/drives")
    public List<DriveSummary> openDrives() {
        return service.openDrives();
    }

    @GetMapping("/drives/{driveId}")
    public DriveResponse openDrive(@PathVariable Long driveId) {
        return service.openDrive(driveId);
    }

    @PostMapping("/drives/{driveId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public MyApplicationResponse apply(@PathVariable Long driveId, @Valid @RequestBody ApplyRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        return service.apply(driveId, userId(jwt), request);
    }

    @GetMapping("/applications/mine")
    public List<MyApplicationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.mine(userId(jwt));
    }

    @PostMapping("/applications/{applicationId}/withdraw")
    public MyApplicationResponse withdraw(@PathVariable Long applicationId, @AuthenticationPrincipal Jwt jwt) {
        return service.withdraw(applicationId, userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
