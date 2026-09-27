package com.clubhub.membership;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ClubMemberControllerTest {

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioning;
    @Autowired UserRepository users;

    UUID clubId;
    String slug;
    UUID adminId;
    RequestPostProcessor asAdmin;

    /** Fresh club per test: tests stay independent regardless of execution order. */
    @BeforeEach
    void newClub() {
        slug = "mem_" + UUID.randomUUID().toString().substring(0, 8);
        adminId = newUserId(users);
        clubId = provisioning.provision(slug, "Members Club", adminId).getId();
        asAdmin = inClub(adminId, clubId, slug);
    }

    @Test
    void adminAddsAMemberByEmailAndEveryoneCanSeeTheList() throws Exception {
        UUID student = newUserId(users);

        add(asAdmin, email(student), "MEMBER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.email").value(email(student)));

        // the new member can list the club's members (read access for any member)
        mvc.perform(get("/api/club/members").with(inClub(student, clubId, slug)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].role").value("CLUB_ADMIN"));
    }

    @Test
    void duplicateAndUnknownEmailsAreRejected() throws Exception {
        UUID student = newUserId(users);
        add(asAdmin, email(student), "MEMBER").andExpect(status().isCreated());

        add(asAdmin, email(student), "CORE").andExpect(status().isConflict());
        add(asAdmin, "never.registered@srmist.edu.in", "MEMBER").andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsCanManageMembers() throws Exception {
        UUID core = newUserId(users);
        add(asAdmin, email(core), "CORE").andExpect(status().isCreated());

        add(inClub(core, clubId, slug), email(newUserId(users)), "MEMBER")
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/club/members/" + adminId).with(inClub(core, clubId, slug)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPromotesAndRemovesMembers() throws Exception {
        UUID student = newUserId(users);
        add(asAdmin, email(student), "MEMBER").andExpect(status().isCreated());

        mvc.perform(patch("/api/club/members/" + student).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"CORE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CORE"));

        mvc.perform(delete("/api/club/members/" + student).with(asAdmin))
                .andExpect(status().isNoContent());

        // removal is effective immediately, even though their token still names this club
        mvc.perform(get("/api/club/profile").with(inClub(student, clubId, slug)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theLastAdminCannotBeDemotedOrRemoved() throws Exception {
        mvc.perform(patch("/api/club/members/" + adminId).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A club must keep at least one CLUB_ADMIN"));
        mvc.perform(delete("/api/club/members/" + adminId).with(asAdmin))
                .andExpect(status().isConflict());

        // with a second admin, stepping down is allowed
        UUID coAdmin = newUserId(users);
        add(asAdmin, email(coAdmin), "CLUB_ADMIN").andExpect(status().isCreated());
        mvc.perform(delete("/api/club/members/" + adminId).with(asAdmin))
                .andExpect(status().isNoContent());
    }

    private ResultActions add(RequestPostProcessor auth, String email, String role) throws Exception {
        return mvc.perform(post("/api/club/members").with(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"));
    }

    private String email(UUID userId) {
        return users.findById(userId).orElseThrow().getEmail();
    }
}
