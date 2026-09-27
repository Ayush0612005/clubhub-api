package com.clubhub.recruitment;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.membership.ClubRole;
import com.clubhub.membership.Membership;
import com.clubhub.membership.MembershipRepository;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClubDriveControllerTest {

    private static final String DRIVES = "/api/club/recruitment/drives";

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;
    @Autowired MembershipRepository memberships;

    RequestPostProcessor asAdmin;
    RequestPostProcessor asMember;

    @BeforeAll
    void createClub() {
        UUID adminId = newUserId(users);
        UUID memberId = newUserId(users);
        UUID clubId = provisioningService.provision("drive_club", "Drive Club", adminId).getId();
        memberships.save(new Membership(memberId, clubId, ClubRole.MEMBER));
        asAdmin = inClub(adminId, clubId, "drive_club");
        asMember = inClub(memberId, clubId, "drive_club");
    }

    long createDrive(String title) throws Exception {
        String body = mvc.perform(post(DRIVES).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"Hiring for 2026",
                                 "questions":[{"prompt":"Why join?","required":true},
                                              {"prompt":"GitHub link","required":false}]}""".formatted(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.questions[0].sortOrder").value(1))
                .andExpect(jsonPath("$.questions[1].prompt").value("GitHub link"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    void changeStatus(long id, String target, int expectedStatus) throws Exception {
        mvc.perform(patch(DRIVES + "/" + id + "/status").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void coreCreatesAndOpensADrive() throws Exception {
        long id = createDrive("Tech team");
        changeStatus(id, "OPEN", 200);

        mvc.perform(get(DRIVES + "/" + id).with(asMember))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void draftsAreHiddenFromPlainMembers() throws Exception {
        long id = createDrive("Secret draft");

        mvc.perform(get(DRIVES).with(asMember))
                .andExpect(jsonPath("$[*].title", not(hasItem("Secret draft"))));
        mvc.perform(get(DRIVES + "/" + id).with(asMember)).andExpect(status().isNotFound());
        mvc.perform(get(DRIVES).with(asAdmin))
                .andExpect(jsonPath("$[*].title", hasItem("Secret draft")));
    }

    @Test
    void enforcesDriveLifecycle() throws Exception {
        long id = createDrive("Lifecycle");
        changeStatus(id, "CLOSED", 409); // DRAFT cannot skip straight to CLOSED
        changeStatus(id, "OPEN", 200);
        changeStatus(id, "CLOSED", 200);
        changeStatus(id, "DRAFT", 409);  // never back to draft
        changeStatus(id, "OPEN", 200);   // reopen is allowed
    }

    @Test
    void membersCannotManageDrives() throws Exception {
        mvc.perform(post(DRIVES).with(asMember)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Nope","questions":[{"prompt":"?","required":true}]}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidDrive() throws Exception {
        mvc.perform(post(DRIVES).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","closesAt":"2020-01-01T00:00:00Z","questions":[]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownDriveIs404() throws Exception {
        mvc.perform(get(DRIVES + "/999999").with(asAdmin)).andExpect(status().isNotFound());
    }
}
