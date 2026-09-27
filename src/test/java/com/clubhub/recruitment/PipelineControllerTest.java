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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static com.clubhub.support.TestAuth.asUser;
import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PipelineControllerTest {

    private static final String SLUG = "pipeline_club";

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;
    @Autowired MembershipRepository memberships;

    UUID clubId;
    RequestPostProcessor asAdmin;
    RequestPostProcessor asPlainMember;

    @BeforeAll
    void createClub() {
        UUID adminId = newUserId(users);
        UUID memberId = newUserId(users);
        clubId = provisioningService.provision(SLUG, "Pipeline Club", adminId).getId();
        memberships.save(new Membership(memberId, clubId, ClubRole.MEMBER));
        asAdmin = inClub(adminId, clubId, SLUG);
        asPlainMember = inClub(memberId, clubId, SLUG);
    }

    static long id(ResultActions result, String path) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    /** Creates and opens a drive with one required question; returns [driveId, questionId]. */
    long[] openDrive(String title) throws Exception {
        ResultActions created = mvc.perform(post("/api/club/recruitment/drives").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","questions":[{"prompt":"Why join?","required":true}]}"""
                                .formatted(title)))
                .andExpect(status().isCreated());
        long driveId = id(created, "$.id");
        mvc.perform(patch("/api/club/recruitment/drives/" + driveId + "/status").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk());
        return new long[]{driveId, id(created, "$.questions[0].id")};
    }

    long applyAs(UUID student, long[] drive) throws Exception {
        return id(mvc.perform(post("/api/clubs/" + SLUG + "/recruitment/drives/" + drive[0] + "/applications")
                        .with(asUser(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":%d,\"answer\":\"Because\"}]}".formatted(drive[1])))
                .andExpect(status().isCreated()), "$.id");
    }

    ResultActions move(long applicationId, String target) throws Exception {
        return mvc.perform(post("/api/club/recruitment/applications/" + applicationId + "/transitions")
                .with(asAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\",\"note\":\"moved to %s\"}".formatted(target, target)));
    }

    @Test
    void selectingAnApplicantMakesThemAMember() throws Exception {
        long[] drive = openDrive("Selection drive");
        UUID student = newUserId(users);
        long appId = applyAs(student, drive);

        move(appId, "SHORTLISTED").andExpect(status().isOk());
        move(appId, "INTERVIEW").andExpect(status().isOk());
        move(appId, "SELECTED").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SELECTED"))
                .andExpect(jsonPath("$.answers[0].prompt").value("Why join?"))
                .andExpect(jsonPath("$.history.length()").value(4)) // APPLIED + 3 moves
                .andExpect(jsonPath("$.history[3].note").value("moved to SELECTED"));

        assertThat(memberships.findByUserIdAndTenantId(student, clubId))
                .get().extracting(Membership::getRole).isEqualTo(ClubRole.MEMBER);
        // and the new member can immediately use the member-only club API
        mvc.perform(get("/api/club/profile").with(inClub(student, clubId, SLUG))).andExpect(status().isOk());
    }

    @Test
    void enforcesThePipelineOrder() throws Exception {
        long appId = applyAs(newUserId(users), openDrive("Order drive"));

        move(appId, "SELECTED").andExpect(status().isConflict());  // must be shortlisted + interviewed first
        move(appId, "WITHDRAWN").andExpect(status().isConflict()); // only the applicant withdraws
        move(appId, "REJECTED").andExpect(status().isOk());
        move(appId, "SHORTLISTED").andExpect(status().isConflict()); // rejected is final
    }

    @Test
    void listsApplicantsPagedAndFilteredByStatus() throws Exception {
        long[] drive = openDrive("List drive");
        long first = applyAs(newUserId(users), drive);
        applyAs(newUserId(users), drive);
        move(first, "SHORTLISTED").andExpect(status().isOk());

        String url = "/api/club/recruitment/drives/" + drive[0] + "/applications";
        mvc.perform(get(url).param("size", "1").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].applicant.email").exists());
        mvc.perform(get(url).param("status", "SHORTLISTED").with(asAdmin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first));
    }

    @Test
    void plainMembersCannotSeeApplicants() throws Exception {
        long[] drive = openDrive("Private drive");
        long appId = applyAs(newUserId(users), drive);

        mvc.perform(get("/api/club/recruitment/drives/" + drive[0] + "/applications").with(asPlainMember))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/club/recruitment/applications/" + appId).with(asPlainMember))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownIdsAre404() throws Exception {
        mvc.perform(get("/api/club/recruitment/drives/999999/applications").with(asAdmin))
                .andExpect(status().isNotFound());
        move(999999, "SHORTLISTED").andExpect(status().isNotFound());
    }
}
