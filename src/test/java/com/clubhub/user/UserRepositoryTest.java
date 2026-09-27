package com.clubhub.user;

import com.clubhub.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Autowired UserRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void storesNormalizedEmailAndHashedPassword() {
        repository.saveAndFlush(new User("  Ayush@SRMIST.edu.in ", encoder.encode("s3cret-pass"), "Ayush K"));
        entityManager.clear();

        User found = repository.findByEmail(User.normalizeEmail("AYUSH@srmist.edu.in")).orElseThrow();

        assertThat(found.getEmail()).isEqualTo("ayush@srmist.edu.in");
        assertThat(found.getPlatformRole()).isEqualTo(PlatformRole.USER);
        assertThat(found.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("s3cret-pass");
        assertThat(encoder.matches("s3cret-pass", found.getPasswordHash())).isTrue();
        assertThat(encoder.matches("wrong-pass", found.getPasswordHash())).isFalse();
    }

    @Test
    void databaseRejectsSameEmailWithDifferentCase() {
        repository.saveAndFlush(new User("dup@srmist.edu.in", encoder.encode("x-password"), "First"));

        // bypass the entity's normalization to prove the DB index itself enforces it
        assertThatThrownBy(() -> entityManager.createNativeQuery("""
                        INSERT INTO users (id, email, password_hash, full_name)
                        VALUES (gen_random_uuid(), 'DUP@srmist.edu.in', 'x', 'Second')""")
                .executeUpdate())
                .isInstanceOf(Exception.class)
                .hasMessageContaining("uk_users_email_lower");
    }

    @Test
    void existsByEmail() {
        repository.saveAndFlush(new User("exists@srmist.edu.in", encoder.encode("x-password"), "Someone"));

        assertThat(repository.existsByEmail("exists@srmist.edu.in")).isTrue();
        assertThat(repository.existsByEmail("nobody@srmist.edu.in")).isFalse();
    }
}
