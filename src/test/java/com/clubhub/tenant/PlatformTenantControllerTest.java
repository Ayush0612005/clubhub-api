package com.clubhub.tenant;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.membership.ClubRole;
import com.clubhub.membership.MembershipRepository;
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

import java.util.UUID;

import static com.clubhub.support.TestAuth.asUser;
import static com.clubhub.support.TestAuth.newUserId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformTenantControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TenantRepository tenants;
    @Autowired MembershipRepository memberships;

    UUID creatorId;

    @BeforeAll
    void createCaller() {
        creatorId = newUserId(users);
    }

    @Test
    void createsClubAndReturns201() throws Exception {
        mvc.perform(post("/api/platform/tenants").with(asUser(creatorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"api_chess_club","name":"Chess Club"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.slug").value("api_chess_club"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        mvc.perform(get("/api/platform/tenants").with(asUser(creatorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("api_chess_club")));
    }

    @Test
    void creatorBecomesClubAdmin() throws Exception {
        mvc.perform(post("/api/platform/tenants").with(asUser(creatorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"api_owned_club","name":"Owned Club"}"""))
                .andExpect(status().isCreated());

        UUID tenantId = tenants.findBySlug("api_owned_club").orElseThrow().getId();
        assertThat(memberships.findByUserIdAndTenantId(creatorId, tenantId))
                .get().extracting(m -> m.getRole()).isEqualTo(ClubRole.CLUB_ADMIN);
    }

    @Test
    void duplicateSlugIs409() throws Exception {
        String body = """
                {"slug":"api_quiz_club","name":"Quiz Club"}""";
        mvc.perform(post("/api/platform/tenants").with(asUser(creatorId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/platform/tenants").with(asUser(creatorId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Club with slug 'api_quiz_club' already exists"));
    }

    @Test
    void invalidBodyIs400ProblemDetail() throws Exception {
        mvc.perform(post("/api/platform/tenants").with(asUser(creatorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slug":"Bad Slug","name":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"));
    }
}
