package com.clubhub.demo;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.support.TestAuth;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemoApiTest {

    @Autowired MockMvc mvc;
    @Autowired DemoSandbox sandbox;
    @Autowired UserRepository users;
    @Autowired TenantProvisioningService provisioning;

    private String demoLogin() throws Exception {
        String body = mvc.perform(post("/api/auth/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.club.slug").value(DemoAccounts.CLUB_SLUG))
                .andExpect(jsonPath("$.club.role").value("CLUB_ADMIN"))
                .andReturn().getResponse().getContentAsString();
        return body;
    }

    private static String bearer(String tokenResponse) {
        return "Bearer " + JsonPath.read(tokenResponse, "$.accessToken");
    }

    @Test
    void oneClickLandsInASeededClubAsItsAdmin() throws Exception {
        String auth = bearer(demoLogin());

        mvc.perform(get("/api/club/members").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[*].fullName", hasItems("Demo Visitor", "Ananya Iyer", "Kavya Nair")));
        mvc.perform(get("/api/club/recruitment/drives").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItems("Core team 2026: build & software", "Tech fest volunteers",
                        "RoboWars pit crew")));
        mvc.perform(get("/api/club/events").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Line-follower build night")));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(2));
    }

    @Test
    void theVisitorCannotTouchAnythingRealStudentsSee() throws Exception {
        String auth = bearer(demoLogin());

        mvc.perform(post("/api/campus/suggestions/events").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Spam\",\"startsAt\":\"2030-01-01T10:00:00Z\"}"))
                .andExpect(status().isForbidden());
        String realClub = "real_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        provisioning.provision(realClub, "A Real Club");
        mvc.perform(post("/api/clubs/" + realClub + "/recruitment/drives/1/applications").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[]}"))
                .andExpect(status().isForbidden());

        // real students can't be pulled into the sandbox
        String realEmail = users.findById(TestAuth.newUserId(users)).orElseThrow().getEmail();
        mvc.perform(post("/api/club/members").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + realEmail + "\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict());

        // and the account has no password that works
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoAccounts.VISITOR_EMAIL + "\",\"password\":\"anything-at-all\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void resetWipesChangesButKeepsVisitorSessionsValid() throws Exception {
        String tokens = demoLogin();
        String auth = bearer(tokens);

        mvc.perform(post("/api/club/members").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoSandbox.emailFor("Ishaan Gupta") + "\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/club/members").header("Authorization", auth))
                .andExpect(jsonPath("$[*].fullName", hasItem("Ishaan Gupta")));

        sandbox.resetNow();

        // same token (same tenant id inside it) still works, and the change is gone
        mvc.perform(get("/api/club/members").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[*].fullName", not(hasItem("Ishaan Gupta"))));

        // refreshing keeps the demo session alive even though .invalid isn't an allowed login domain
        String refresh = JsonPath.read(tokens, "$.refreshToken");
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\",\"clubSlug\":\"demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.club.slug").value("demo"));
    }
}
