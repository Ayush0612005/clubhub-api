package com.clubhub.auth;

import com.clubhub.auth.AuthDtos.LoginRequest;
import com.clubhub.auth.AuthDtos.RegisterRequest;
import com.clubhub.auth.AuthDtos.TokenResponse;
import com.clubhub.auth.AuthDtos.UserResponse;
import com.clubhub.auth.AuthExceptions.EmailAlreadyUsedException;
import com.clubhub.auth.AuthExceptions.InvalidCredentialsException;
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
    /** Hash compared against when the email is unknown, so both failure paths take the same time. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
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

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(User.normalizeEmail(request.email()));

        // Always run one BCrypt comparison. Returning early for unknown emails would make those
        // responses measurably faster and let an attacker enumerate registered accounts.
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user.isEmpty() || !passwordMatches || !user.get().isEnabled()) {
            throw new InvalidCredentialsException();
        }

        JwtTokenService.AccessToken token = tokenService.issueAccessToken(user.get());
        return TokenResponse.bearer(token.value(), token.expiresAt());
    }
}
