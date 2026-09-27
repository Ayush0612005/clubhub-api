package com.clubhub.recruitment;

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

import static com.clubhub.support.TestAuth.asUser;
import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A student who is NOT a member browses a club's open drives, applies, and withdraws. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StudentRecruitmentTest {

    private static final String CLUB = "/api/clubs/apply_club/recruitment";

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;

    RequestPostProcessor asAdmin;

    @BeforeAll
    void createClub() {
        UUID adminId = newUserId(users);
        UUID clubId = provisioningService.provision("apply_club", "Apply Club", adminId).getId();
        asAdmin = inClub(adminId, clubId, "apply_club");
    }

    /** Creates a drive through the club API; returns [driveId, requiredQuestionId, optionalQuestionId]. */
    long[] drive(String title, String... statuses) throws Exception {
        String body = mvc.perform(post("/api/club/recruitment/drives").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","questions":[{"prompt":"Why join?","required":true},
                                                           {"prompt":"GitHub link","required":false}]}"""
                                .formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        for (String s : statuses) {
            mvc.perform(patch("/api/club/recruitment/drives/" + id + "/status").with(asAdmin)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + s + "\"}"))
                    .andExpect(status().isOk());
        }
        return new long[]{id,
                ((Number) JsonPath.read(body, "$.questions[0].id")).longValue(),
                ((Number) JsonPath.read(body, "$.questions[1].id")).longValue()};
    }

    ResultActions apply(UUID student, long driveId, String answersJson) throws Exception {
        return mvc.perform(post(CLUB + "/drives/" + driveId + "/applications").with(asUser(student))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":" + answersJson + "}"));
    }

    long applicationId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void nonMemberBrowsesOnlyOpenDrives() throws Exception {
        long[] open = drive("Open drive", "OPEN");
        long[] draft = drive("Draft drive");
        UUID student = newUserId(users);

        mvc.perform(get(CLUB + "/drives").with(asUser(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Open drive")))
                .andExpect(jsonPath("$[*].title", not(hasItem("Draft drive"))));
        mvc.perform(get(CLUB + "/drives/" + open[0]).with(asUser(student)))
                .andExpect(jsonPath("$.questions.length()").value(2));
        mvc.perform(get(CLUB + "/drives/" + draft[0]).with(asUser(student)))
                .andExpect(status().isNotFound());
    }

    @Test
    void appliesOnceWithRequiredAnswers() throws Exception {
        long[] d = drive("Tech drive", "OPEN");
        UUID student = newUserId(users);
        String answers = "[{\"questionId\":%d,\"answer\":\"I build things\"}]".formatted(d[1]);

        apply(student, d[0], answers)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.driveTitle").value("Tech drive"));
        apply(student, d[0], answers).andExpect(status().isConflict());

        mvc.perform(get(CLUB + "/applications/mine").with(asUser(student)))
                .andExpect(jsonPath("$[0].driveTitle").value("Tech drive"));
    }

    @Test
    void rejectsIncompleteOrForeignAnswers() throws Exception {
        long[] d = drive("Strict drive", "OPEN");
        long[] other = drive("Other drive", "OPEN");
        UUID student = newUserId(users);

        // only the optional question answered: required one missing
        apply(student, d[0], "[{\"questionId\":%d,\"answer\":\"github.com/me\"}]".formatted(d[2]))
                .andExpect(status().isBadRequest());
        // question from another drive
        apply(student, d[0], "[{\"questionId\":%d,\"answer\":\"x\"}]".formatted(other[1]))
                .andExpect(status().isBadRequest());
        // same question twice
        apply(student, d[0], "[{\"questionId\":%1$d,\"answer\":\"a\"},{\"questionId\":%1$d,\"answer\":\"b\"}]"
                .formatted(d[1]))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closedDriveRejectsApplications() throws Exception {
        long[] d = drive("Closed drive", "OPEN", "CLOSED");
        apply(newUserId(users), d[0], "[{\"questionId\":%d,\"answer\":\"late\"}]".formatted(d[1]))
                .andExpect(status().isConflict());
    }

    @Test
    void studentWithdrawsOnlyTheirOwnApplication() throws Exception {
        long[] d = drive("Withdraw drive", "OPEN");
        UUID student = newUserId(users);
        long appId = applicationId(apply(student, d[0],
                "[{\"questionId\":%d,\"answer\":\"hi\"}]".formatted(d[1])).andExpect(status().isCreated()));

        mvc.perform(post(CLUB + "/applications/" + appId + "/withdraw").with(asUser(newUserId(users))))
                .andExpect(status().isNotFound());
        mvc.perform(post(CLUB + "/applications/" + appId + "/withdraw").with(asUser(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        mvc.perform(post(CLUB + "/applications/" + appId + "/withdraw").with(asUser(student)))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownClubIs404AndAnonymousIs401() throws Exception {
        mvc.perform(get("/api/clubs/no_such_club/recruitment/drives").with(asUser(newUserId(users))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/clubs/Bad-Slug!/recruitment/drives").with(asUser(newUserId(users))))
                .andExpect(status().isNotFound());
        mvc.perform(get(CLUB + "/drives")).andExpect(status().isUnauthorized());
    }
}
