package com.clubhub.auth;

import com.clubhub.auth.AuthDtos.ClubTokenResponse;
import com.clubhub.auth.AuthDtos.LoginRequest;
import com.clubhub.auth.AuthDtos.RefreshRequest;
import com.clubhub.auth.AuthDtos.SwitchClubRequest;
import com.clubhub.auth.AuthDtos.RegisterRequest;
import com.clubhub.auth.AuthDtos.TokenResponse;
import com.clubhub.auth.AuthDtos.EmailRequest;
import com.clubhub.auth.AuthDtos.RegisterResponse;
import com.clubhub.auth.AuthDtos.ResetPasswordRequest;
import com.clubhub.auth.AuthDtos.TokenRequest;
import com.clubhub.security.JwtTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountRecoveryService recovery;

    public AuthController(AuthService authService, AccountRecoveryService recovery) {
        this.authService = authService;
        this.recovery = recovery;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody TokenRequest request) {
        recovery.verifyEmail(request.token());
    }

    /** Always 202, whether or not the address has an unverified account. */
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody EmailRequest request) {
        recovery.resendVerification(request.email());
    }

    /** Always 202, whether or not the address has an account. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody EmailRequest request) {
        recovery.requestPasswordReset(request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        recovery.resetPassword(request.token(), request.password());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** "Try the demo": signs in as the demo visitor, admin of a sandbox club that resets itself hourly. */
    @PostMapping("/demo")
    public TokenResponse demo() {
        return authService.demoLogin();
    }

    /** Trade a refresh token for a new access + refresh token pair (the old refresh token dies). */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken(), request.clubSlug());
    }

    /** Needs a valid access token; returns a new one scoped to the requested club. */
    @PostMapping("/switch-club")
    public ClubTokenResponse switchClub(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SwitchClubRequest request) {
        return authService.switchClub(UUID.fromString(jwt.getSubject()), request.clubSlug());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    /** Who does this token belong to? Read straight from the verified JWT, no DB hit. */
    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> me = new LinkedHashMap<>();
        me.put("id", jwt.getSubject());
        me.put("email", jwt.getClaimAsString(JwtTokenService.CLAIM_EMAIL));
        me.put("roles", jwt.getClaimAsStringList(JwtTokenService.CLAIM_ROLES));
        me.put("club", jwt.getClaimAsString(JwtTokenService.CLAIM_CLUB));           // null if no active club
        me.put("clubRole", jwt.getClaimAsString(JwtTokenService.CLAIM_CLUB_ROLE));
        return me;
    }
}
