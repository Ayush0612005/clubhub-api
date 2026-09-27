package com.clubhub.security;

import com.clubhub.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test (no Spring context, no Docker): the crypto contract of our tokens. */
class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes!!";

    private final JwtConfig config = new JwtConfig();
    private final JwtProperties props = new JwtProperties(SECRET, "clubhub-api", Duration.ofMinutes(15), Duration.ofDays(14));
    private final SecretKey key = config.jwtSigningKey(props);
    private final JwtDecoder decoder = config.jwtDecoder(key, props);
    private final JwtTokenService service = new JwtTokenService(config.jwtEncoder(key), props);

    @Test
    void issuedTokenCarriesIdentityAndRole() {
        User user = user();
        JwtTokenService.AccessToken token = service.issueAccessToken(user);

        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("jwt@srmist.edu.in");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        // getIssuer() expects a URL; our issuer is a plain identifier, so read the raw claim
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("clubhub-api");
        assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt());
    }

    @Test
    void rejectsTamperedPayload() {
        String token = service.issueAccessToken(user()).value();
        String[] parts = token.split("\\.");
        // flip one character in the payload: signature no longer matches
        char c = parts[1].charAt(5);
        parts[1] = parts[1].substring(0, 5) + (c == 'A' ? 'B' : 'A') + parts[1].substring(6);

        assertThatThrownBy(() -> decoder.decode(String.join(".", parts))).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtProperties otherProps = new JwtProperties("another-secret-that-is-also-32-bytes-long!!", "clubhub-api",
                Duration.ofMinutes(15), Duration.ofDays(14));
        JwtTokenService attacker = new JwtTokenService(config.jwtEncoder(config.jwtSigningKey(otherProps)), otherProps);

        assertThatThrownBy(() -> decoder.decode(attacker.issueAccessToken(user()).value()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        JwtTokenService pastService = new JwtTokenService(config.jwtEncoder(key), props, twoHoursAgo);

        assertThatThrownBy(() -> decoder.decode(pastService.issueAccessToken(user()).value()))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsForeignIssuer() {
        JwtProperties foreign = new JwtProperties(SECRET, "someone-else", Duration.ofMinutes(15), Duration.ofDays(14));
        JwtTokenService foreignService = new JwtTokenService(config.jwtEncoder(key), foreign);

        assertThatThrownBy(() -> decoder.decode(foreignService.issueAccessToken(user()).value()))
                .isInstanceOf(JwtException.class);
    }

    private static User user() {
        User user = new User("jwt@srmist.edu.in", "{noop}x", "Jwt User");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
