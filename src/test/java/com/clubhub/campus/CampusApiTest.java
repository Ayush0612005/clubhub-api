package com.clubhub.campus;

import com.clubhub.TestcontainersConfiguration;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.clubhub.support.TestAuth.asPlatformAdmin;
import static com.clubhub.support.TestAuth.asUser;
import static com.clubhub.support.TestAuth.newUserId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CampusApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SrmEventsImporter importer;

    UUID student;
    UUID admin;

    @BeforeAll
    void users() {
        student = newUserId(users);
        admin = newUserId(users);
    }

    @Test
    void everyStudentSeesAllSeededSrmClubs() throws Exception {
        mvc.perform(get("/api/campus/clubs").with(asUser(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(75)))
                .andExpect(jsonPath("$[*].slug", hasItem("google-developer-group")))
                .andExpect(jsonPath("$[*].slug", hasItem("dance-club")));

        mvc.perform(get("/api/campus/clubs/team-robocon").with(asUser(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Team Robocon"))
                .andExpect(jsonPath("$.workspaceSlug").doesNotExist());
    }

    @Test
    void studentSuggestionIsHiddenUntilAnAdminApprovesIt() throws Exception {
        String title = "Robo Wars " + UUID.randomUUID();
        String body = mvc.perform(post("/api/campus/suggestions/events").with(asUser(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("team-robocon", title, inDays(10), "https://forms.gle/robowars")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/campus/events").with(asUser(student)))
                .andExpect(jsonPath("$[*].title", not(hasItem(title))));

        mvc.perform(post("/api/platform/campus/events/" + id + "/approve").with(asPlatformAdmin(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(get("/api/campus/events").with(asUser(student)))
                .andExpect(jsonPath("$[*].title", hasItem(title)));
        mvc.perform(get("/api/campus/clubs/team-robocon").with(asUser(student)))
                .andExpect(jsonPath("$.events[*].title", hasItem(title)))
                .andExpect(jsonPath("$.events[0].club.slug").value("team-robocon"));
    }

    @Test
    void rejectedAndPastEventsNeverShow() throws Exception {
        String rejected = "Rejected " + UUID.randomUUID();
        String id = JsonPath.read(mvc.perform(post("/api/campus/suggestions/events").with(asUser(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event(null, rejected, inDays(5), null)))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/platform/campus/events/" + id + "/reject").with(asPlatformAdmin(admin)))
                .andExpect(status().isOk());

        String past = "Last week " + UUID.randomUUID();
        mvc.perform(post("/api/platform/campus/events").with(asPlatformAdmin(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event(null, past, inDays(-7), null)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/campus/events").with(asUser(student)))
                .andExpect(jsonPath("$[*].title", not(hasItem(rejected))))
                .andExpect(jsonPath("$[*].title", not(hasItem(past))))
                .andExpect(jsonPath("$[*].status", everyItem(org.hamcrest.Matchers.is("APPROVED"))));
    }

    @Test
    void recruitmentsShowUntilTheirDeadline() throws Exception {
        String open = "Tech team " + UUID.randomUUID();
        String closed = "Old drive " + UUID.randomUUID();
        mvc.perform(post("/api/platform/campus/recruitments").with(asPlatformAdmin(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recruitment("google-developer-group", open, LocalDate.now().plusDays(3))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/platform/campus/recruitments").with(asPlatformAdmin(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recruitment("google-developer-group", closed, LocalDate.now().minusDays(3))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/campus/recruitments").with(asUser(student)))
                .andExpect(jsonPath("$[*].title", hasItem(open)))
                .andExpect(jsonPath("$[*].title", not(hasItem(closed))));
        mvc.perform(get("/api/campus/clubs").with(asUser(student)))
                .andExpect(jsonPath("$[?(@.slug == 'google-developer-group')].recruiting").value(hasItem(true)));
    }

    @Test
    void anyoneCanBrowseButSuggestingNeedsAnAccount() throws Exception {
        mvc.perform(get("/api/campus/clubs")).andExpect(status().isOk());
        mvc.perform(get("/api/campus/clubs/team-robocon")).andExpect(status().isOk());
        mvc.perform(get("/api/campus/events")).andExpect(status().isOk());
        mvc.perform(get("/api/campus/recruitments")).andExpect(status().isOk());

        mvc.perform(post("/api/campus/suggestions/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event(null, "Anonymous", inDays(2), null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/platform/campus/queue")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentsCannotModerate() throws Exception {
        mvc.perform(get("/api/platform/campus/queue").with(asUser(student))).andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/campus/events").with(asUser(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event(null, "Sneaky", inDays(1), null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsNonWebLinksAndUnknownClubs() throws Exception {
        mvc.perform(post("/api/campus/suggestions/events").with(asUser(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event(null, "XSS attempt", inDays(1), "javascript:alert(1)")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/campus/suggestions/events").with(asUser(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("no-such-club", "Ghost", inDays(1), null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStudentCanHaveOnlyAFewSuggestionsWaiting() throws Exception {
        UUID eager = newUserId(users);
        for (int i = 0; i < CampusService.MAX_PENDING_PER_STUDENT; i++) {
            mvc.perform(post("/api/campus/suggestions/events").with(asUser(eager))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(event(null, "Idea " + i, inDays(3), null)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/campus/suggestions/events").with(asUser(eager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event(null, "One too many", inDays(3), null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void feedImportQueuesUpcomingItemsOnceAndSkipsPastOnes() throws Exception {
        int next = LocalDate.now().getYear() + 1;
        String upcomingGuid = "https://www.srmist.edu.in/?p=" + UUID.randomUUID();
        String xml = SrmFeedParserTest.feed("""
                <item><title>Hackathon %1$d</title><link>https://www.srmist.edu.in/events/hack-%1$d/</link>
                  <guid>%2$s</guid><description>Hackathon on 14 March %1$d at the Tech Park.</description></item>
                <item><title>Alumni meet report</title><link>https://www.srmist.edu.in/events/alumni/</link>
                  <guid>%3$s</guid><description>The meet was held on 22 August 2020 in Delhi.</description></item>
                """.formatted(next, upcomingGuid, "https://www.srmist.edu.in/?p=" + UUID.randomUUID()));

        var first = importer.importFeed(xml);
        assertThat(first.added()).isEqualTo(1);
        assertThat(first.skippedPast()).isEqualTo(1);
        assertThat(importer.importFeed(xml).alreadyKnown()).isEqualTo(1);

        mvc.perform(get("/api/platform/campus/queue").with(asPlatformAdmin(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].event.title", hasItem("Hackathon " + next)))
                .andExpect(jsonPath("$.events[?(@.event.title == 'Hackathon %d')].submittedBy".formatted(next))
                        .value(hasItem("SRM events feed")));
        // imported items wait for review: not visible to students yet
        mvc.perform(get("/api/campus/events").with(asUser(student)))
                .andExpect(jsonPath("$[*].title", not(hasItem("Hackathon " + next))));
    }

    private static Instant inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    private static String event(String clubSlug, String title, Instant startsAt, String registrationUrl) {
        return """
                {"clubSlug":%s,"title":"%s","startsAt":"%s","venue":"TP Ganesan Auditorium","registrationUrl":%s}"""
                .formatted(quoted(clubSlug), title, startsAt, quoted(registrationUrl));
    }

    private static String recruitment(String clubSlug, String title, LocalDate deadline) {
        return """
                {"clubSlug":"%s","title":"%s","applyUrl":"https://forms.gle/apply","deadline":"%s"}"""
                .formatted(clubSlug, title, deadline);
    }

    private static String quoted(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }
}
