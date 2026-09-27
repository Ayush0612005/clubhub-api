package com.clubhub.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solves the "first admin" chicken-and-egg problem: nobody can call admin endpoints until an
 * admin exists. On startup, the account named by CLUBHUB_ADMIN_EMAIL is promoted.
 *
 * It only PROMOTES an account that already registered normally. It never creates one, so no
 * password ever has to live in an environment variable or a config file.
 */
@Component
public class PlatformAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final UserRepository userRepository;
    private final String adminEmail;

    public PlatformAdminBootstrap(UserRepository userRepository,
                                  @Value("${clubhub.bootstrap.platform-admin-email:}") String adminEmail) {
        this.userRepository = userRepository;
        this.adminEmail = adminEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        promote(adminEmail);
    }

    void promote(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(User.normalizeEmail(email)).ifPresentOrElse(user -> {
            if (user.getPlatformRole() != PlatformRole.PLATFORM_ADMIN) {
                user.promoteToPlatformAdmin();
                log.info("Promoted {} to PLATFORM_ADMIN (takes effect on their next login)", user.getEmail());
            }
        }, () -> log.warn("CLUBHUB_ADMIN_EMAIL is set to {} but no such account exists yet: register it, then restart",
                email));
    }
}
