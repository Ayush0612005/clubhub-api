package com.clubhub.plan;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static com.clubhub.support.TestAuth.asPlatformAdmin;
import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlanTest {

    private static final String SLUG = "plan_club";

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;

    UUID clubId;
    RequestPostProcessor asAdmin;
    RequestPostProcessor asPlatform;

    @BeforeAll
    void createFreeClub() throws Exception {
        UUID adminId = newUserId(users);
        clubId = provisioningService.provision(SLUG, "Plan Club", adminId).getId();
        asAdmin = inClub(adminId, clubId, SLUG);
        asPlatform = asPlatformAdmin(newUserId(users));
        mvc.perform(patch("/api/platform/tenants/" + clubId + "/plan").with(asPlatform)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"FREE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"));
    }

    ResultActions openNewDrive(String title) throws Exception {
        String body = mvc.perform(post("/api/club/recruitment/drives").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","questions":[{"prompt":"Why?","required":true}]}""".formatted(title)))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        return mvc.perform(patch("/api/club/recruitment/drives/" + id + "/status").with(asAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}"));
    }

    @Test
    void freePlanCapsOpenDrivesAndShowsUsage() throws Exception {
        openNewDrive("Drive 1").andExpect(status().isOk());
        openNewDrive("Drive 2").andExpect(status().isOk());
        openNewDrive("Drive 3")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT"));

        mvc.perform(get("/api/club/plan").with(asAdmin))
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.limits.OPEN_DRIVES").value(Plan.FREE.maxOpenDrives()))
                .andExpect(jsonPath("$.usage.OPEN_DRIVES").value(2))
                .andExpect(jsonPath("$.features.EVENT_POSTERS").value(false));
    }

    @Test
    void featuresFollowThePlanUnlessOverridden() throws Exception {
        String upload = """
                {"purpose":"EVENT_POSTER","eventId":1,"contentType":"image/png","sizeBytes":1000}""";
        mvc.perform(post("/api/club/files/uploads").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(upload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_IN_PLAN"));

        // certificates are in FREE, but the platform admin can switch them off for one club
        mvc.perform(put("/api/platform/tenants/" + clubId + "/features/CERTIFICATES").with(asPlatform)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/club/events/1/certificates").with(asAdmin))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/club/plan").with(asAdmin))
                .andExpect(jsonPath("$.features.CERTIFICATES").value(false));
    }

    @Test
    void onlyThePlatformAdminChangesPlans() throws Exception {
        mvc.perform(patch("/api/platform/tenants/" + clubId + "/plan").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PRO\"}"))
                .andExpect(status().isForbidden());
    }
}
