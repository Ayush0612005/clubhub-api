package com.clubhub.tenancy;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.tenant.TenantProvisioningService;
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
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    void createClubs() {
        provisioningService.provision("filter_active", "Active Club");
        provisioningService.provision("filter_suspended", "Suspended Club");
        jdbc.update("UPDATE tenants SET status = 'SUSPENDED' WHERE slug = 'filter_suspended'");
    }

    @Test
    void knownClubRunsRequestInsideItsSchema() throws Exception {
        mvc.perform(get("/api/club/_probe").header("X-Tenant-ID", "filter_active"))
                .andExpect(status().isOk())
                .andExpect(content().string("club_filter_active"));
    }

    @Test
    void missingHeaderIs400() throws Exception {
        mvc.perform(get("/api/club/_probe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Missing X-Tenant-ID header"));
    }

    @Test
    void unknownClubIs404() throws Exception {
        mvc.perform(get("/api/club/_probe").header("X-Tenant-ID", "no_such_club"))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedSlugIs404WithoutEchoingInput() throws Exception {
        mvc.perform(get("/api/club/_probe").header("X-Tenant-ID", "\"><script>"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Unknown club"));
    }

    @Test
    void suspendedClubIs403() throws Exception {
        mvc.perform(get("/api/club/_probe").header("X-Tenant-ID", "filter_suspended"))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformEndpointsAreNotTenantScoped() throws Exception {
        mvc.perform(get("/api/platform/_probe").header("X-Tenant-ID", "filter_active"))
                .andExpect(status().isOk())
                .andExpect(content().string("none"));
    }
}
