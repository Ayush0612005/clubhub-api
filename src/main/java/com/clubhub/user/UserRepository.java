package com.clubhub.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // emails are stored normalized (trimmed, lower-case), so callers pass User.normalizeEmail(input)
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
