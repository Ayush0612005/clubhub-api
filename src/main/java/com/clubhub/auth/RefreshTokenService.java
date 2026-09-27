package com.clubhub.auth;

import com.clubhub.auth.AuthExceptions.InvalidRefreshTokenException;
import com.clubhub.security.JwtProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens with rotation and reuse detection.
 *
 * - Token = 256 random bits; the client gets the raw value, the DB stores only SHA-256(raw).
 *   (SHA-256, not BCrypt: the input is already high-entropy random, and we need an indexed lookup.)
 * - Every use rotates: the presented token is revoked and a successor in the same family is issued.
 * - Presenting an already-rotated token means it was copied (stolen or replayed): the whole family
 *   is revoked, logging out both the attacker and the victim's session.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }

    public record Rotation(UUID userId, IssuedRefreshToken next) {
    }

    /** New login = new family. */
    @Transactional
    public IssuedRefreshToken issueForNewLogin(UUID userId) {
        return issue(userId, UUID.randomUUID()).issued();
    }

    /**
     * noRollbackFor: on reuse we revoke the family AND throw. Without it, the exception would roll
     * back the revocation and the stolen family would stay valid.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken current = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = Instant.now();

        if (current.isRevoked()) {
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {}: revoked {} token(s) in family {}",
                    current.getUserId(), revoked, current.getFamilyId());
            throw new InvalidRefreshTokenException();
        }
        if (current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        Issued next = issue(current.getUserId(), current.getFamilyId());
        current.markRotated(next.entity().getId(), now);
        return new Rotation(current.getUserId(), next.issued());
    }

    /** Logout: ends this login session (its whole family). Idempotent, unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is guaranteed by the JDK", e);
        }
    }

    private Issued issue(UUID userId, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(properties.refreshTokenTtl());

        RefreshToken saved = repository.save(new RefreshToken(userId, familyId, hash(raw), expiresAt));
        return new Issued(saved, new IssuedRefreshToken(raw, expiresAt));
    }

    private record Issued(RefreshToken entity, IssuedRefreshToken issued) {
    }
}
