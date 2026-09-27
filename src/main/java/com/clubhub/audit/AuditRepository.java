package com.clubhub.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {

    Page<AuditEntry> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);

    Page<AuditEntry> findAllByActionOrderByOccurredAtDescIdDesc(String action, Pageable pageable);
}
