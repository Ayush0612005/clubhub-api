package com.clubhub.auth;

import com.clubhub.auth.AuthDtos.ActiveClub;
import com.clubhub.auth.AuthDtos.ClubTokenResponse;
import com.clubhub.auth.AuthDtos.LoginRequest;
import com.clubhub.auth.AuthDtos.RegisterRequest;
import com.clubhub.auth.AuthDtos.RegisterResponse;
import com.clubhub.auth.AuthExceptions.EmailNotVerifiedException;
import org.springframework.beans.factory.annotation.Value;
import com.clubhub.auth.AuthDtos.TokenResponse;
import com.clubhub.auth.AuthDtos.UserResponse;
import com.clubhub.auth.AuthExceptions.EmailAlreadyUsedException;
import com.clubhub.auth.AuthExceptions.InvalidCredentialsException;
import com.clubhub.auth.AuthExceptions.InvalidRefreshTokenException;
import com.clubhub.auth.RefreshTokenService.IssuedRefreshToken;
import com.clubhub.auth.RefreshTokenService.Rotation;
import com.clubhub.demo.DemoAccounts;
import com.clubhub.demo.DemoSandbox;
import com.clubhub.security.JwtTokenService;
import com.clubhub.security.JwtTokenService.AccessToken;
import com.clubhub.security.JwtTokenService.ClubClaims;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final ClubAccessService clubAccessService;
    private final EmailPolicy emailPolicy;
    private final EmailTokenService emailTokens;
    private final AccountEmailService accountEmails;
    private final DemoSandbox demoSandbox;
    /** On: new accounts must click an emailed link before they can log in. */
    private final boolean requireEmailVerification;
    /** Hash compared against when the email is unknown, so both failure paths take the same time. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenService tokenService, RefreshTokenService refreshTokenService,
                       ClubAccessService clubAccessService, EmailPolicy emailPolicy,
                       EmailTokenService emailTokens, AccountEmailService accountEmails, DemoSandbox demoSandbox,
                       @Value("${clubhub.auth.email-verification:false}") boolean requireEmailVerification) {
        this.demoSandbox = demoSandbox;
        this.emailPolicy = emailPolicy;
        this.emailTokens = emailTokens;
        this.accountEmails = accountEmails;
        this.requireEmailVerification = requireEmailVerification;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.clubAccessService = clubAccessService;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        emailPolicy.requireAllowed(email);
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        try {
            User newUser = new User(email, passwordEncoder.encode(request.password()), request.fullName().trim());
            if (!requireEmailVerification) {
                newUser.markEmailVerified(); // admitted under the rules of the time; stays valid if verification is switched on later
            }
            User user = userRepository.saveAndFlush(newUser);
            if (requireEmailVerification) {
                accountEmails.sendVerification(user.getEmail(), user.getFullName(),
                        emailTokens.issue(user.getId(), EmailToken.Purpose.VERIFY_EMAIL));
            }
            return new RegisterResponse(user.getId(), user.getEmail(), user.getFullName(), requireEmailVerification);
        } catch (DataIntegrityViolationException e) {
            // two concurrent registrations passed existsByEmail; the unique index caught the second
            throw new EmailAlreadyUsedException();
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        // checked before any lookup: the rule is public, so this answer can't reveal whether the account exists
        emailPolicy.requireAllowed(request.email());
        Optional<User> user = userRepository.findByEmail(User.normalizeEmail(request.email()));

        // Always run one BCrypt comparison. Returning early for unknown emails would make those
        // responses measurably faster and let an attacker enumerate registered accounts.
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user.isEmpty() || !passwordMatches || !user.get().isEnabled()) {
            throw new InvalidCredentialsException();
        }
        // after the password check: only the real owner learns the account exists but is unconfirmed
        if (requireEmailVerification && !user.get().isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        IssuedRefreshToken refresh = refreshTokenService.issueForNewLogin(user.get().getId());
        return tokens(user.get(), refresh, Optional.empty());
    }

    /**
     * One-click sign-in as the demo visitor, already switched into the demo club as its admin.
     * No password involved: the account can't do anything outside its sandbox club, which
     * resets itself (see DemoSandbox).
     */
    public TokenResponse demoLogin() {
        User visitor = demoSandbox.prepareVisit();
        IssuedRefreshToken refresh = refreshTokenService.issueForNewLogin(visitor.getId());
        return tokens(visitor, refresh, clubAccessService.findAccess(visitor.getId(), DemoAccounts.CLUB_SLUG));
    }

    /**
     * Deliberately NOT @Transactional. rotate() commits its own transaction, including the family
     * revocation on reuse. An outer transaction here would be marked rollback-only by the
     * InvalidRefreshTokenException and silently undo that revocation.
     *
     * If clubSlug is given, membership is re-checked now. If it was revoked meanwhile, the client
     * still gets a valid (club-less) token pair instead of an error, so the session survives.
     */
    public TokenResponse refresh(String refreshToken, String clubSlug) {
        Rotation rotation = refreshTokenService.rotate(refreshToken);
        User user = userRepository.findById(rotation.userId())
                .filter(User::isEnabled)
                // pre-rule accounts can't keep a session alive; the demo visitor is outside the rule by design
                .filter(u -> emailPolicy.isAllowed(u.getEmail())
                        || (demoSandbox.isEnabled() && DemoAccounts.VISITOR_EMAIL.equals(u.getEmail())))
                .orElseThrow(InvalidRefreshTokenException::new);
        Optional<ClubClaims> club = clubSlug == null || clubSlug.isBlank()
                ? Optional.empty()
                : clubAccessService.findAccess(user.getId(), clubSlug);
        return tokens(user, rotation.next(), club);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    /** Re-issues the access token scoped to one club, after checking the user belongs to it. */
    @Transactional(readOnly = true)
    public ClubTokenResponse switchClub(UUID userId, String clubSlug) {
        User user = userRepository.findById(userId)
                .filter(User::isEnabled)
                .orElseThrow(InvalidCredentialsException::new);
        ClubClaims club = clubAccessService.requireAccess(userId, clubSlug);
        AccessToken access = tokenService.issueClubAccessToken(user, club);
        return new ClubTokenResponse(access.value(), "Bearer", access.expiresAt(), activeClub(club));
    }

    private TokenResponse tokens(User user, IssuedRefreshToken refresh, Optional<ClubClaims> club) {
        AccessToken access = club.map(c -> tokenService.issueClubAccessToken(user, c))
                .orElseGet(() -> tokenService.issueAccessToken(user));
        return TokenResponse.bearer(access.value(), access.expiresAt(), refresh.value(), refresh.expiresAt(),
                club.map(AuthService::activeClub).orElse(null));
    }

    private static ActiveClub activeClub(ClubClaims club) {
        return new ActiveClub(club.slug(), club.role());
    }
}
