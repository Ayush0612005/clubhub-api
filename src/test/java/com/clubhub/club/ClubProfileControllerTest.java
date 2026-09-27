package com.clubhub.club;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static com.clubhub.support.TestAuth.asUser;
import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClubProfileControllerTest {

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;

    UUID adminId;
    RequestPostProcessor inProfileClub;
    RequestPostProcessor inEditClub;

    @BeforeAll
    void createClubs() {
        adminId = newUserId(users);
        UUID profileClub = provisioningService.provision("profile_club", "Profile Club", adminId).getId();
        // own club for the update test: test order is not guaranteed
        UUID editClub = provisioningService.provision("profile_edit_club", "Editable Club", adminId).getId();
        inProfileClub = inClub(adminId, profileClub, "profile_club");
        inEditClub = inClub(adminId, editClub, "profile_edit_club");
    }

    @Test
    void returnsSeededProfile() throws Exception {
        mvc.perform(get("/api/club/profile").with(inProfileClub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Profile Club"));
    }

    @Test
    void updatesProfile() throws Exception {
        mvc.perform(put("/api/club/profile").with(inEditClub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Profile Club KTR",
                                 "description":"We run workshops every Friday",
                                 "contactEmail":"profile@srmist.edu.in"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Profile Club KTR"));

        mvc.perform(get("/api/club/profile").with(inEditClub))
                .andExpect(jsonPath("$.contactEmail").value("profile@srmist.edu.in"));
    }

    @Test
    void rejectsInvalidUpdate() throws Exception {
        mvc.perform(put("/api/club/profile").with(inProfileClub)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"","contactEmail":"not-an-email"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAnActiveClubInTheToken() throws Exception {
        mvc.perform(get("/api/club/profile").with(asUser(adminId)))
                .andExpect(status().isForbidden());
    }
}
