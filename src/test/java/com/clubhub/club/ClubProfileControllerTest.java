package com.clubhub.club;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.tenant.TenantProvisioningService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClubProfileControllerTest {

    private static final String TENANT = "X-Tenant-ID";

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;

    @BeforeAll
    void createClub() {
        provisioningService.provision("profile_club", "Profile Club");
        provisioningService.provision("profile_edit_club", "Editable Club"); // own club: test order is not guaranteed
    }

    @Test
    void returnsSeededProfile() throws Exception {
        mvc.perform(get("/api/club/profile").header(TENANT, "profile_club"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Profile Club"));
    }

    @Test
    void updatesProfile() throws Exception {
        mvc.perform(put("/api/club/profile").header(TENANT, "profile_edit_club")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Profile Club KTR",
                                 "description":"We run workshops every Friday",
                                 "contactEmail":"profile@srmist.edu.in"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Profile Club KTR"));

        mvc.perform(get("/api/club/profile").header(TENANT, "profile_edit_club"))
                .andExpect(jsonPath("$.contactEmail").value("profile@srmist.edu.in"));
    }

    @Test
    void rejectsInvalidUpdate() throws Exception {
        mvc.perform(put("/api/club/profile").header(TENANT, "profile_club")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"","contactEmail":"not-an-email"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresTenantHeader() throws Exception {
        mvc.perform(get("/api/club/profile"))
                .andExpect(status().isBadRequest());
    }
}
