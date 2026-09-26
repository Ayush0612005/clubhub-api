package com.clubhub.tenant;

import com.clubhub.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TenantProvisioningServiceTest {

    @Autowired
    TenantProvisioningService provisioningService;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void provisionsSchemaAndRegistersTenant() {
        Tenant tenant = provisioningService.provision("music_club", "Music Club");

        assertThat(tenant.getId()).isNotNull();
        assertThat(tenantRepository.findBySlug("music_club")).isPresent();
        assertThat(tableExists("club_music_club", "club_profile")).isTrue();
    }

    @Test
    void seedsDefaultProfileInsideTheClubSchema() {
        provisioningService.provision("photo_club", "Photography Club");

        String displayName = jdbc.queryForObject(
                "SELECT display_name FROM club_photo_club.club_profile", String.class);
        assertThat(displayName).isEqualTo("Photography Club");
    }

    @Test
    void rejectsDuplicateSlug() {
        provisioningService.provision("dance_club", "Dance Club");

        assertThatThrownBy(() -> provisioningService.provision("dance_club", "Dance Club Again"))
                .isInstanceOf(TenantAlreadyExistsException.class);
    }

    @Test
    void rejectsInvalidSlugBeforeCreatingAnySchema() {
        assertThatThrownBy(() -> provisioningService.provision("Bad-Slug", "Bad"))
                .isInstanceOf(IllegalArgumentException.class);

        Integer schemas = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name ILIKE 'club_bad%'",
                Integer.class);
        assertThat(schemas).isZero();
    }

    private boolean tableExists(String schema, String table) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                Integer.class, schema, table);
        return count != null && count == 1;
    }
}
