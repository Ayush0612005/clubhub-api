package com.clubhub;

import com.clubhub.tenant.TenantRepository;
import com.clubhub.user.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.clubhub.support.TestAuth.asUser;
import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance test: drives the HTTP API and proves one club can never see or change another
 * club's data, including when an Alpha member presents a token pointing at Beta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantIsolationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired TenantRepository tenants;

    UUID alphaAdmin;
    UUID betaAdmin;
    UUID alphaId;
    UUID betaId;

    @BeforeAll
    void onboardTwoClubsThroughTheApi() throws Exception {
        alphaAdmin = newUserId(users);
        betaAdmin = newUserId(users);
        createClub(alphaAdmin, "iso_alpha", "Alpha Society");
        createClub(betaAdmin, "iso_beta", "Beta Society");
        alphaId = tenants.findBySlug("iso_alpha").orElseThrow().getId();
        betaId = tenants.findBySlug("iso_beta").orElseThrow().getId();
    }

    @Test
    void writesInOneClubAreInvisibleToAnother() throws Exception {
        mvc.perform(put("/api/club/profile").with(inClub(alphaAdmin, alphaId, "iso_alpha"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Alpha Secret Plans","contactEmail":"alpha@srmist.edu.in"}"""))
                .andExpect(status().isOk());

        // Beta still sees only its own, untouched profile
        mvc.perform(get("/api/club/profile").with(inClub(betaAdmin, betaId, "iso_beta")))
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
    void alphaMemberCannotReachBetaEvenWithBetasIdInTheToken() throws Exception {
        // what the old X-Tenant-ID header allowed: just point the request at another club
        mvc.perform(get("/api/club/profile").with(inClub(alphaAdmin, betaId, "iso_beta")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/club/profile").with(inClub(alphaAdmin, betaId, "iso_beta"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Hijacked"}"""))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT display_name FROM club_iso_beta.club_profile", String.class))
                .isNotEqualTo("Hijacked");
    }

    @Test
    void unknownClubIsRejectedAndTouchesNothing() throws Exception {
        mvc.perform(put("/api/club/profile").with(inClub(alphaAdmin, UUID.randomUUID(), "iso_gamma"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Should never be stored"}"""))
                .andExpect(status().isForbidden());

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

    private void createClub(UUID ownerId, String slug, String name) throws Exception {
        mvc.perform(post("/api/platform/tenants").with(asUser(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"%s","name":"%s"}""".formatted(slug, name)))
                .andExpect(status().isCreated());
    }
}
