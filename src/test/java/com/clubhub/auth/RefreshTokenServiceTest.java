package com.clubhub.auth;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.auth.AuthExceptions.InvalidRefreshTokenException;
import com.clubhub.auth.RefreshTokenService.IssuedRefreshToken;
import com.clubhub.auth.RefreshTokenService.Rotation;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @SpringBootTest (no test-managed transaction) on purpose: we must observe what really gets
 * committed, e.g. that the family revocation survives the exception thrown on reuse.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RefreshTokenServiceTest {

    @Autowired RefreshTokenService service;
    @Autowired RefreshTokenRepository repository;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void storesOnlyTheHash() {
        UUID userId = newUser();
        IssuedRefreshToken token = service.issueForNewLogin(userId);

        Integer rawStored = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, token.value());
        Integer hashStored = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", Integer.class, RefreshTokenService.hash(token.value()));

        assertThat(rawStored).isZero();
        assertThat(hashStored).isOne();
    }

    @Test
    void rotationIssuesANewTokenAndTheNewOneWorks() {
        UUID userId = newUser();
        IssuedRefreshToken first = service.issueForNewLogin(userId);

        Rotation rotation = service.rotate(first.value());

        assertThat(rotation.userId()).isEqualTo(userId);
        assertThat(rotation.next().value()).isNotEqualTo(first.value());
        assertThat(service.rotate(rotation.next().value()).userId()).isEqualTo(userId);
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() {
        UUID userId = newUser();
        IssuedRefreshToken stolen = service.issueForNewLogin(userId);
        IssuedRefreshToken legit = service.rotate(stolen.value()).next(); // victim rotates normally

        // attacker replays the old token
        assertThatThrownBy(() -> service.rotate(stolen.value())).isInstanceOf(InvalidRefreshTokenException.class);

        // ...and the victim's current token is dead too: the revocation was committed despite the exception
        assertThatThrownBy(() -> service.rotate(legit.value())).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void otherLoginSessionsSurviveAReuseAttack() {
        UUID userId = newUser();
        IssuedRefreshToken laptop = service.issueForNewLogin(userId);
        IssuedRefreshToken phone = service.issueForNewLogin(userId);
        service.rotate(laptop.value());

        assertThatThrownBy(() -> service.rotate(laptop.value())).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(service.rotate(phone.value()).userId()).isEqualTo(userId);
    }

    @Test
    void expiredUnknownAndLoggedOutTokensAreRejected() {
        UUID userId = newUser();
        repository.save(new RefreshToken(userId, UUID.randomUUID(), RefreshTokenService.hash("expired-raw"),
                Instant.now().minusSeconds(1)));
        assertThatThrownBy(() -> service.rotate("expired-raw")).isInstanceOf(InvalidRefreshTokenException.class);

        assertThatThrownBy(() -> service.rotate("never-issued")).isInstanceOf(InvalidRefreshTokenException.class);

        IssuedRefreshToken session = service.issueForNewLogin(userId);
        service.revoke(session.value());
        assertThatThrownBy(() -> service.rotate(session.value())).isInstanceOf(InvalidRefreshTokenException.class);
    }

    private UUID newUser() {
        return users.save(new User(UUID.randomUUID() + "@srmist.edu.in", "{noop}x", "Refresh Test")).getId();
    }
}
