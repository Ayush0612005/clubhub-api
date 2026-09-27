package com.clubhub.auth;

import com.clubhub.auth.AuthDtos.ClubTokenResponse;
import com.clubhub.auth.AuthDtos.LoginRequest;
import com.clubhub.auth.AuthDtos.RefreshRequest;
import com.clubhub.auth.AuthDtos.SwitchClubRequest;
import com.clubhub.auth.AuthDtos.RegisterRequest;
import com.clubhub.auth.AuthDtos.TokenResponse;
import com.clubhub.auth.AuthDtos.UserResponse;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
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
