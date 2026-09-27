package com.clubhub.notification;

import com.clubhub.common.PageResponse;
import com.clubhub.notification.NotificationService.NotificationView;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** The caller's own inbox (across all clubs). No club context needed. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<NotificationView> inbox(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return service.inbox(userId(jwt), unreadOnly, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100)));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("unread", service.unreadCount(userId(jwt)));
    }

    @PostMapping("/{id}/read")
    public NotificationView markRead(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.markRead(id, userId(jwt));
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("marked", service.markAllRead(userId(jwt)));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
