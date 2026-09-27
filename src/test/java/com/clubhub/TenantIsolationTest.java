package com.clubhub;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1 acceptance test: drives the public HTTP API only and proves that one club
 * can never see or change another club's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser // endpoints require authentication now; real token flow is covered by SecurityRulesTest
class TenantIsolationTest {

    private static final String TENANT = "X-Tenant-ID";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    void onboardTwoClubsThroughTheApi() throws Exception {
        createClub("iso_alpha", "Alpha Society");
        createClub("iso_beta", "Beta Society");
    }

    @Test
    void writesInOneClubAreInvisibleToAnother() throws Exception {
        mvc.perform(put("/api/club/profile").header(TENANT, "iso_alpha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Alpha Secret Plans","contactEmail":"alpha@srmist.edu.in"}"""))
                .andExpect(status().isOk());

        // Beta still sees only its own, untouched profile
        mvc.perform(get("/api/club/profile").header(TENANT, "iso_beta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Beta Society"))
                .andExpect(jsonPath("$.contactEmail").doesNotExist());

        // and at the storage level the data lives only in Alpha's schema
        assertThat(jdbc.queryForObject("SELECT display_name FROM club_iso_alpha.club_profile", String.class))
                .isEqualTo("Alpha Secret Plans");
        assertThat(jdbc.queryForObject("SELECT display_name FROM club_iso_beta.club_profile", String.class))
                .isEqualTo("Beta Society");
    }

    @Test
    void unknownClubIs404AndTouchesNothing() throws Exception {
        mvc.perform(put("/api/club/profile").header(TENANT, "iso_gamma")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Should never be stored"}"""))
                .andExpect(status().isNotFound());

        Integer schemas = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'club_iso_gamma'", Integer.class);
        assertThat(schemas).isZero();
    }

    @Test
    void clubDataNeverLandsInPublicSchema() {
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'club_profile'",
                Integer.class);
        assertThat(tables).isZero();
    }

    private void createClub(String slug, String name) throws Exception {
        mvc.perform(post("/api/platform/tenants")
                        // called from @BeforeAll, where @WithMockUser does not apply: authenticate this request explicitly
                        .with(user("setup-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","name":"%s"}""".formatted(slug, name)))
                .andExpect(status().isCreated());
    }
}
