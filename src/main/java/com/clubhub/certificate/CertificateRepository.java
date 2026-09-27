package com.clubhub.certificate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    List<Certificate> findAllByUserIdOrderByIssuedAtDesc(UUID userId);

    List<Certificate> findAllByEventId(Long eventId);
}
