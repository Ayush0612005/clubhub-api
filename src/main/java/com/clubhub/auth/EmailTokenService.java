package com.clubhub.auth;

import com.clubhub.auth.AuthExceptions.InvalidEmailTokenException;
import com.clubhub.auth.EmailToken.Purpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Issues and redeems single-use email links (256 random bits, stored as SHA-256). */
@Service
public class EmailTokenService {

    static final Duration VERIFY_TTL = Duration.ofHours(24);
    static final Duration RESET_TTL = Duration.ofMinutes(30); // short: a reset link is as good as a password
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailTokenRepository repository;

    public EmailTokenService(EmailTokenRepository repository) {
        this.repository = repository;
    }

    /** Returns the raw token to put in the email link. Older unused links of the same purpose stop working. */
    @Transactional
    public String issue(UUID userId, Purpose purpose) {
        Instant now = Instant.now();
        repository.invalidateUnused(userId, purpose, now);

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Duration ttl = purpose == Purpose.VERIFY_EMAIL ? VERIFY_TTL : RESET_TTL;
        repository.save(new EmailToken(userId, purpose, RefreshTokenService.hash(raw), now.plus(ttl)));
        return raw;
    }

    /** True if a link of this purpose went to this user within the cooldown: don't flood their inbox. */
    @Transactional(readOnly = true)
    public boolean sentRecently(UUID userId, Purpose purpose) {
        return repository.existsByUserIdAndPurposeAndCreatedAtAfter(userId, purpose, Instant.now().minus(RESEND_COOLDOWN));
    }

    /** Marks the token used and returns its user. Unknown, expired, used or wrong-purpose tokens all fail the same way. */
    @Transactional
    public UUID consume(String rawToken, Purpose purpose) {
        Instant now = Instant.now();
        EmailToken token = repository.findByTokenHash(RefreshTokenService.hash(rawToken))
                .filter(t -> t.getPurpose() == purpose && t.isUsable(now))
                .orElseThrow(InvalidEmailTokenException::new);
        token.markUsed(now);
        return token.getUserId();
    }
}
