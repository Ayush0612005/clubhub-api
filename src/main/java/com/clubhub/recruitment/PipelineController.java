package com.clubhub.recruitment;

import com.clubhub.common.PageResponse;
import com.clubhub.recruitment.PipelineDtos.ApplicationDetail;
import com.clubhub.recruitment.PipelineDtos.ApplicationSummary;
import com.clubhub.recruitment.PipelineDtos.TransitionRequest;
import com.clubhub.tenancy.CurrentMember;
import com.clubhub.tenancy.CurrentMember.ClubMember;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reviewing applicants of the caller's active club: CORE and above only (applicant data is personal). */
@RestController
@RequestMapping("/api/club/recruitment")
@PreAuthorize("@clubAuthz.atLeast('CORE')")
public class PipelineController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PipelineService service;

    public PipelineController(PipelineService service) {
        this.service = service;
    }

    @GetMapping("/drives/{driveId}/applications")
    public PageResponse<ApplicationSummary> list(@PathVariable Long driveId,
                                                 @RequestParam(required = false) ApplicationStatus status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by("submittedAt").ascending()); // first come, first reviewed
        return service.list(driveId, status, pageable);
    }

    @GetMapping("/applications/{applicationId}")
    public ApplicationDetail get(@PathVariable Long applicationId) {
        return service.get(applicationId);
    }

    @PostMapping("/applications/{applicationId}/transitions")
    public ApplicationDetail transition(@PathVariable Long applicationId,
                                        @Valid @RequestBody TransitionRequest request) {
        ClubMember reviewer = CurrentMember.get().orElseThrow(); // bound by TenantFilter
        return service.transition(applicationId, request.status(), request.note(),
                reviewer.userId(), reviewer.tenantId());
    }
}
