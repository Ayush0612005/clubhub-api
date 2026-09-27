package com.clubhub.auth;

import com.clubhub.auth.AuthDtos.ActiveClub;
import com.clubhub.auth.AuthDtos.ClubTokenResponse;
import com.clubhub.auth.AuthDtos.LoginRequest;
import com.clubhub.auth.AuthDtos.RegisterRequest;
import com.clubhub.auth.AuthDtos.TokenResponse;
import com.clubhub.auth.AuthDtos.UserResponse;
import com.clubhub.auth.AuthExceptions.EmailAlreadyUsedException;
import com.clubhub.auth.AuthExceptions.InvalidCredentialsException;
import com.clubhub.auth.AuthExceptions.InvalidRefreshTokenException;
import com.clubhub.auth.RefreshTokenService.IssuedRefreshToken;
import com.clubhub.auth.RefreshTokenService.Rotation;
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
    /** Hash compared against when the email is unknown, so both failure paths take the same time. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenService tokenService, RefreshTokenService refreshTokenService,
                       ClubAccessService clubAccessService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.clubAccessService = clubAccessService;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        try {
            User user = userRepository.saveAndFlush(
                    new User(email, passwordEncoder.encode(request.password()), request.fullName().trim()));
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName());
        } catch (DataIntegrityViolationException e) {
            // two concurrent registrations passed existsByEmail; the unique index caught the second
            throw new EmailAlreadyUsedException();
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(User.normalizeEmail(request.email()));

        // Always run one BCrypt comparison. Returning early for unknown emails would make those
        // responses measurably faster and let an attacker enumerate registered accounts.
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user.isEmpty() || !passwordMatches || !user.get().isEnabled()) {
            throw new InvalidCredentialsException();
        }
        IssuedRefreshToken refresh = refreshTokenService.issueForNewLogin(user.get().getId());
        return tokens(user.get(), refresh, Optional.empty());
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
