package com.clubhub.notification;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.membership.ClubRole;
import com.clubhub.membership.Membership;
import com.clubhub.membership.MembershipRepository;
import com.clubhub.tenant.Tenant;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

/** Full path: service transaction commits → Kafka (Testcontainers) → consumer → inbox API. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationFlowTest {

    private static final String SLUG = "notify_club";

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;
    @Autowired MembershipRepository memberships;
    @Autowired NotificationService notificationService;

    Tenant club;
    UUID adminId;
    UUID memberId;
    RequestPostProcessor asAdmin;

    @BeforeAll
    void createClub() {
        adminId = newUserId(users);
        memberId = newUserId(users);
        club = provisioningService.provision(SLUG, "Notify Club", adminId);
        memberships.save(new Membership(memberId, club.getId(), ClubRole.MEMBER));
        asAdmin = inClub(adminId, club.getId(), SLUG);
    }

    /** Polls the user's inbox until it holds a notification of this type (consumption is asynchronous). */
    String awaitNotification(UUID userId, String type) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            String json = mvc.perform(get("/api/notifications").with(asUser(userId)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<String> types = JsonPath.read(json, "$.content[*].type");
            if (types.contains(type)) {
                return json;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("No " + type + " notification for " + userId + " within 30 s");
    }

    long number(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    @Test
    void applicantIsNotifiedWhenShortlisted() throws Exception {
        String drive = mvc.perform(post("/api/club/recruitment/drives").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Design team","questions":[{"prompt":"Why?","required":true}]}"""))
                .andReturn().getResponse().getContentAsString();
        long driveId = number(drive, "$.id");
        long questionId = number(drive, "$.questions[0].id");
        mvc.perform(patch("/api/club/recruitment/drives/" + driveId + "/status").with(asAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}")).andExpect(status().isOk());

        UUID student = newUserId(users);
        String app = mvc.perform(post("/api/clubs/" + SLUG + "/recruitment/drives/" + driveId + "/applications")
                        .with(asUser(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":%d,\"answer\":\"I like design\"}]}".formatted(questionId)))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/club/recruitment/applications/" + number(app, "$.id") + "/transitions").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SHORTLISTED\"}"))
                .andExpect(status().isOk());

        String inbox = awaitNotification(student, "APPLICATION_STATUS_CHANGED");
        assertThat((String) JsonPath.read(inbox, "$.content[0].body"))
                .isEqualTo("Your application for \"Design team\" is now Shortlisted.");
    }

    @Test
    void publishingAnEventNotifiesEveryMember() throws Exception {
        Instant start = Instant.now().plus(Duration.ofDays(2));
        String event = mvc.perform(post("/api/club/events").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Git workshop","venue":"TP 101","startsAt":"%s","endsAt":"%s","visibility":"MEMBERS"}"""
                                .formatted(start, start.plus(Duration.ofHours(2)))))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/club/events/" + number(event, "$.id") + "/publish").with(asAdmin))
                .andExpect(status().isOk());

        awaitNotification(memberId, "EVENT_PUBLISHED");
        awaitNotification(adminId, "EVENT_PUBLISHED");
    }

    @Test
    void aRedeliveredEventDoesNotNotifyTwice() {
        UUID user = newUserId(users);
        var event = new DomainEvent.CertificateIssued(UUID.randomUUID(), DomainEvent.Club.of(club), Instant.now(),
                user, UUID.randomUUID(), "Certificate of Participation");

        assertThat(notificationService.record(event)).hasSize(1);
        assertThat(notificationService.record(event)).isEmpty(); // same id: Kafka redelivery
        assertThat(notificationService.unreadCount(user)).isEqualTo(1);
    }

    @Test
    void readStateIsPerUser() throws Exception {
        UUID user = newUserId(users);
        for (int i = 0; i < 3; i++) {
            notificationService.record(new DomainEvent.CertificateIssued(UUID.randomUUID(), DomainEvent.Club.of(club),
                    Instant.now(), user, UUID.randomUUID(), "Certificate " + i));
        }
        String inbox = mvc.perform(get("/api/notifications").with(asUser(user)))
                .andExpect(jsonPath("$.totalElements").value(3)).andReturn().getResponse().getContentAsString();
        long firstId = number(inbox, "$.content[0].id");

        // someone else can't mark my notification read
        mvc.perform(post("/api/notifications/" + firstId + "/read").with(asUser(newUserId(users))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/" + firstId + "/read").with(asUser(user)))
                .andExpect(jsonPath("$.read").value(true));
        mvc.perform(get("/api/notifications/unread-count").with(asUser(user)))
                .andExpect(jsonPath("$.unread").value(2));
        mvc.perform(post("/api/notifications/read-all").with(asUser(user)))
                .andExpect(jsonPath("$.marked").value(2));
        mvc.perform(get("/api/notifications").param("unreadOnly", "true").with(asUser(user)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
