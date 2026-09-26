package com.clubhub.tenant;

import com.clubhub.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TenantSchemaMigratorTest {

    @Autowired
    TenantSchemaMigrator migrator;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsSchemaWithTenantTables() {
        migrator.migrate("club_migrator_test");

        assertThat(tablesIn("club_migrator_test"))
                .contains("club_profile", "flyway_schema_history");
        // tenant tables must NOT leak into public
        assertThat(tablesIn("public")).doesNotContain("club_profile");
    }

    @Test
    void isIdempotent() {
        migrator.migrate("club_idempotent_test");

        assertThatCode(() -> migrator.migrate("club_idempotent_test")).doesNotThrowAnyException();
    }

    private java.util.List<String> tablesIn(String schema) {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ?",
                String.class, schema);
    }
}
