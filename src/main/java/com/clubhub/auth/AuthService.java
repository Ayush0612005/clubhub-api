package com.clubhub.auth;

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
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    /** Hash compared against when the email is unknown, so both failure paths take the same time. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenService tokenService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
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
        return tokensFor(user.get(), refreshTokenService.issueForNewLogin(user.get().getId()));
    }

    /**
     * Deliberately NOT @Transactional. rotate() commits its own transaction, including the family
     * revocation on reuse. An outer transaction here would be marked rollback-only by the
     * InvalidRefreshTokenException and silently undo that revocation.
     */
    public TokenResponse refresh(String refreshToken) {
        Rotation rotation = refreshTokenService.rotate(refreshToken);
        User user = userRepository.findById(rotation.userId())
                .filter(User::isEnabled)
                .orElseThrow(InvalidRefreshTokenException::new);
        return tokensFor(user, rotation.next());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private TokenResponse tokensFor(User user, IssuedRefreshToken refresh) {
        JwtTokenService.AccessToken access = tokenService.issueAccessToken(user);
        return TokenResponse.bearer(access.value(), access.expiresAt(), refresh.value(), refresh.expiresAt());
    }
}
