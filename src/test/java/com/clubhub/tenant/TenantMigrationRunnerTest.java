package com.clubhub.tenant;

import com.clubhub.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TenantMigrationRunnerTest {

    @Autowired
    TenantMigrationRunner runner;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migratesSchemasOfRegisteredTenants() {
        // Simulates a club whose schema is behind (here: missing entirely),
        // e.g. a new tenant migration was added since the club was created.
        tenantRepository.saveAndFlush(new Tenant("drama_club", "Drama Club"));
        assertThat(clubProfileTableExists("club_drama_club")).isFalse();

        runner.run(new DefaultApplicationArguments());

        assertThat(clubProfileTableExists("club_drama_club")).isTrue();
    }

    private boolean clubProfileTableExists(String schema) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'club_profile'",
                Integer.class, schema);
        return count != null && count == 1;
    }
}
