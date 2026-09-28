package com.clubhub.auth;

import com.clubhub.auth.AuthExceptions.InvalidEmailTokenException;
import com.clubhub.auth.EmailToken.Purpose;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Email verification and password reset.
 *
 * resend/forgot never say whether the address has an account: the controller always answers 202,
 * and the email itself goes out asynchronously, so response time doesn't leak it either.
 */
@Service
public class AccountRecoveryService {

    private final UserRepository users;
    private final EmailTokenService tokens;
    private final AccountEmailService emails;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;

    public AccountRecoveryService(UserRepository users, EmailTokenService tokens, AccountEmailService emails,
                                  RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.tokens = tokens;
        this.emails = emails;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        UUID userId = tokens.consume(rawToken, Purpose.VERIFY_EMAIL);
        users.findById(userId).orElseThrow(InvalidEmailTokenException::new).markEmailVerified();
    }

    @Transactional
    public void resendVerification(String email) {
        users.findByEmail(User.normalizeEmail(email))
                .filter(user -> user.isEnabled() && !user.isEmailVerified())
                .filter(user -> !tokens.sentRecently(user.getId(), Purpose.VERIFY_EMAIL))
                .ifPresent(user -> emails.sendVerification(user.getEmail(), user.getFullName(),
                        tokens.issue(user.getId(), Purpose.VERIFY_EMAIL)));
    }

    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmail(User.normalizeEmail(email))
                .filter(User::isEnabled)
                .filter(user -> !tokens.sentRecently(user.getId(), Purpose.RESET_PASSWORD))
                .ifPresent(user -> emails.sendPasswordReset(user.getEmail(), user.getFullName(),
                        tokens.issue(user.getId(), Purpose.RESET_PASSWORD)));
    }

    /**
     * Sets the new password and logs out every device. Clicking a reset link also proves the user
     * reads that inbox, so it counts as verifying the email.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        UUID userId = tokens.consume(rawToken, Purpose.RESET_PASSWORD);
        User user = users.findById(userId).orElseThrow(InvalidEmailTokenException::new);
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        user.markEmailVerified();
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }
}
