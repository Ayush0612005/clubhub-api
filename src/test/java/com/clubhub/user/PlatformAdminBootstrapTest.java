package com.clubhub.user;

import com.clubhub.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class PlatformAdminBootstrapTest {

    @Autowired UserRepository users;

    @Test
    void promotesTheConfiguredExistingAccount() {
        users.saveAndFlush(new User("founder@srmist.edu.in", "{noop}x", "Founder"));

        new PlatformAdminBootstrap(users, "  Founder@SRMIST.edu.in ").promote("  Founder@SRMIST.edu.in ");

        assertThat(users.findByEmail("founder@srmist.edu.in").orElseThrow().getPlatformRole())
                .isEqualTo(PlatformRole.PLATFORM_ADMIN);
    }

    @Test
    void unknownOrBlankEmailIsANoOpNotACrash() {
        PlatformAdminBootstrap bootstrap = new PlatformAdminBootstrap(users, "");

        assertThatCode(() -> bootstrap.promote("")).doesNotThrowAnyException();
        assertThatCode(() -> bootstrap.promote("ghost@srmist.edu.in")).doesNotThrowAnyException();
        assertThat(users.findByEmail("ghost@srmist.edu.in")).isEmpty(); // never creates accounts
    }
}
