package com.clubhub.audit;

import com.clubhub.audit.AuditService.AuditView;
import com.clubhub.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The club's audit trail: CLUB_ADMIN only. */
@RestController
@RequestMapping("/api/club/audit")
@PreAuthorize("@clubAuthz.atLeast('CLUB_ADMIN')")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AuditView> list(@RequestParam(required = false) String action,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return service.list(action, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200)));
    }
}
