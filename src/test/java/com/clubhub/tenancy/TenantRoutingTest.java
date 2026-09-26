package com.clubhub.tenancy;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.tenant.TenantProvisioningService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves Hibernate sessions read from the schema chosen by TenantContext. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantRoutingTest {

    @Autowired TenantProvisioningService provisioningService;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    void createTwoClubs() {
        provisioningService.provision("routing_alpha", "Alpha Club");
        provisioningService.provision("routing_beta", "Beta Club");
        jdbc.update("INSERT INTO club_routing_alpha.club_profile (display_name) VALUES ('Alpha profile')");
        jdbc.update("INSERT INTO club_routing_beta.club_profile (display_name) VALUES ('Beta profile')");
    }

    @Test
    void sameQueryReturnsOnlyTheCurrentClubsRows() {
        assertThat(profilesVisibleTo("club_routing_alpha")).containsExactly("Alpha profile");
        assertThat(profilesVisibleTo("club_routing_beta")).containsExactly("Beta profile");
    }

    @Test
    void pooledConnectionsAreResetToPublicAfterUse() {
        profilesVisibleTo("club_routing_alpha");

        // a fresh borrow from the pool must not still point at the club schema
        String schema = jdbc.queryForObject("SELECT current_schema()", String.class);
        assertThat(schema).isEqualTo("public");
    }

    @SuppressWarnings("unchecked")
    private List<String> profilesVisibleTo(String schema) {
        return TenantContext.supplyAs(schema, () -> tx.execute(status ->
                // unqualified table name: resolved via the session's search_path
                (List<String>) entityManager
                        .createNativeQuery("SELECT display_name FROM club_profile")
                        .getResultList()));
    }
}
