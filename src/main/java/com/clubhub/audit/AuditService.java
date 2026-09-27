package com.clubhub.audit;

import com.clubhub.common.PageResponse;
import com.clubhub.tenancy.CurrentMember;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Records who changed what inside a club. Written in the SAME transaction as the change itself
 * (MANDATORY propagation fails fast if a caller forgets its transaction): the change and its audit
 * row commit together or not at all. That's why this doesn't go through Kafka.
 */
@Service
public class AuditService {

    public record AuditView(Long id, UUID actorId, String actorEmail, String action, String targetType,
                            String targetId, Map<String, Object> details, Instant occurredAt) {
    }

    private final AuditRepository audit;
    private final UserRepository users;
    private final JsonMapper json;

    public AuditService(AuditRepository audit, UserRepository users, JsonMapper json) {
        this.audit = audit;
        this.users = users;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String targetType, Object targetId, Map<String, ?> details) {
        UUID actor = CurrentMember.get()
                .orElseThrow(() -> new IllegalStateException("Audit requires a club member context"))
                .userId();
        audit.save(new AuditEntry(actor, action, targetType, String.valueOf(targetId), json.writeValueAsString(details)));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditView> list(String action, Pageable pageable) {
        Page<AuditEntry> page = action == null || action.isBlank()
                ? audit.findAllByOrderByOccurredAtDescIdDesc(pageable)
                : audit.findAllByActionOrderByOccurredAtDescIdDesc(action, pageable);
        Map<UUID, User> actors = users.findAllById(page.getContent().stream().map(AuditEntry::getActorId).distinct()
                .toList()).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return PageResponse.of(page, e -> new AuditView(e.getId(), e.getActorId(),
                actors.containsKey(e.getActorId()) ? actors.get(e.getActorId()).getEmail() : null,
                e.getAction(), e.getTargetType(), e.getTargetId(), parse(e.getDetails()), e.getOccurredAt()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String details) {
        return json.readValue(details, Map.class);
    }
}
