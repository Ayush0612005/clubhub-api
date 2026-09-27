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

/** Issues short-lived access tokens. Claims are kept minimal: identity + platform role. */
@Service
public class JwtTokenService {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLES = "roles";

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

    public AccessToken issueAccessToken(User user) {
        // JWT NumericDate has second precision: truncate so the expiresAt we return equals the token's exp
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(properties.accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())   // stable id, not email: emails can change
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLES, List.of(user.getPlatformRole().name()))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, expiresAt);
    }

    public record AccessToken(String value, Instant expiresAt) {
    }
}
