package com.clubhub.tenant;

import com.clubhub.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class TenantRepositoryTest {

    @Autowired
    TenantRepository repository;

    @Autowired
    EntityManager entityManager;

    @Test
    void savesAndLoadsTenantBySlug() {
        repository.saveAndFlush(new Tenant("coding_club", "Coding Club"));
        entityManager.clear(); // force a real SELECT instead of the 1st-level cache

        Tenant found = repository.findBySlug("coding_club").orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getSchemaName()).isEqualTo("club_coding_club");
        assertThat(found.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(repository.existsBySlug("coding_club")).isTrue();
    }

    @Test
    void rejectsDuplicateSlug() {
        repository.saveAndFlush(new Tenant("robotics", "Robotics Club"));

        assertThatThrownBy(() -> repository.saveAndFlush(new Tenant("robotics", "Another Robotics")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnsafeSlug() {
        // last line of defence: slug ends up inside a schema name
        assertThatThrownBy(() -> repository.saveAndFlush(new Tenant("Drop Table;--", "Evil")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
