package com.clubhub.tenancy;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.user.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.clubhub.support.TestAuth.asUser;
import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
// ProbeController must be imported: Boot's test filter skips classes nested in test classes during scanning
@Import({TestcontainersConfiguration.class, TenantFilterTest.ProbeController.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantFilterTest {

    /** Test-only endpoints that echo the schema the request ended up in. */
    @RestController
    static class ProbeController {
        @GetMapping("/api/club/_probe")
        String club() {
            return TenantContext.currentSchema().orElse("none");
        }

        @GetMapping("/api/platform/_probe")
        String platform() {
            return TenantContext.currentSchema().orElse("none");
        }
    }

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    UUID memberId;
    UUID outsiderId;
    UUID activeClubId;
    UUID suspendedClubId;

    @BeforeAll
    void createClubs() {
        memberId = newUserId(users);
        outsiderId = newUserId(users);
        activeClubId = provisioningService.provision("filter_active", "Active Club", memberId).getId();
        suspendedClubId = provisioningService.provision("filter_suspended", "Suspended Club", memberId).getId();
        jdbc.update("UPDATE tenants SET status = 'SUSPENDED' WHERE slug = 'filter_suspended'");
    }

    @Test
    void memberRunsRequestInsideTheClubSchemaFromTheToken() throws Exception {
        mvc.perform(get("/api/club/_probe").with(inClub(memberId, activeClubId, "filter_active")))
                .andExpect(status().isOk())
                .andExpect(content().string("club_filter_active"));
    }

    @Test
    void tokenWithoutActiveClubIs403() throws Exception {
        mvc.perform(get("/api/club/_probe").with(asUser(memberId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No active club: call POST /api/auth/switch-club first"));
    }

    @Test
    void nonMemberIs403EvenWithAClubIdInTheToken() throws Exception {
        mvc.perform(get("/api/club/_probe").with(inClub(outsiderId, activeClubId, "filter_active")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You are not a member of this club"));
    }

    @Test
    void unknownClubLooksExactlyLikeNonMember() throws Exception {
        mvc.perform(get("/api/club/_probe").with(inClub(memberId, UUID.randomUUID(), "ghost")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You are not a member of this club"));
    }

    @Test
    void suspendedClubIs403() throws Exception {
        mvc.perform(get("/api/club/_probe").with(inClub(memberId, suspendedClubId, "filter_suspended")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Club is suspended"));
    }

    @Test
    void unauthenticatedIs401BeforeAnyTenantLogic() throws Exception {
        mvc.perform(get("/api/club/_probe")).andExpect(status().isUnauthorized());
    }

    @Test
    void platformEndpointsAreNotTenantScoped() throws Exception {
        mvc.perform(get("/api/platform/_probe").with(inClub(memberId, activeClubId, "filter_active")))
                .andExpect(status().isOk())
                .andExpect(content().string("none"));
    }
}
