package com.clubhub.security;

import com.clubhub.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Issues short-lived access tokens: identity + platform role, and optionally the ACTIVE club
 * (tenant id, slug, club role) once the user has switched into one.
 */
@Service
public class JwtTokenService {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TENANT_ID = "tid";
    public static final String CLAIM_CLUB = "club";
    public static final String CLAIM_CLUB_ROLE = "club_role";

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    @Autowired // two constructors: tell Spring which one to use (the other exists for tests)
    public JwtTokenService(JwtEncoder encoder, JwtProperties properties) {
        this(encoder, properties, Clock.systemUTC());
    }

    JwtTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** Token without an active club: enough for platform and account endpoints. */
    public AccessToken issueAccessToken(User user) {
        return issue(user, null);
    }

    /** Token scoped to one club. Only issued after a membership check (see ClubAccessService). */
    public AccessToken issueClubAccessToken(User user, ClubClaims club) {
        return issue(user, club);
    }

    private AccessToken issue(User user, ClubClaims club) {
        // JWT NumericDate has second precision: truncate so the expiresAt we return equals the token's exp
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(properties.accessTokenTtl());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())   // stable id, not email: emails can change
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLES, List.of(user.getPlatformRole().name()));
        if (club != null) {
            claims.claim(CLAIM_TENANT_ID, club.tenantId().toString())
                  .claim(CLAIM_CLUB, club.slug())
                  .claim(CLAIM_CLUB_ROLE, club.role());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new AccessToken(token, expiresAt);
    }

    public record AccessToken(String value, Instant expiresAt) {
    }

    /** The active-club part of a token. Role is a plain String to keep security free of domain types. */
    public record ClubClaims(UUID tenantId, String slug, String role) {
    }
}
