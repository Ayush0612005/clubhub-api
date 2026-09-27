package com.clubhub.recruitment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusChangeRepository extends JpaRepository<ApplicationStatusChange, Long> {

    List<ApplicationStatusChange> findAllByApplicationIdOrderByChangedAtAsc(Long applicationId);
}
