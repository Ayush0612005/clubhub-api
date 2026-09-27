package com.clubhub.event;

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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.clubhub.support.TestAuth.asUser;
import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CheckInTest {

    private static final String SLUG = "door_club";
    private static final String PUBLIC_EVENTS = "/api/clubs/" + SLUG + "/events/";

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;
    @Autowired MembershipRepository memberships;
    @Autowired TicketService tickets;

    RequestPostProcessor asAdmin;
    RequestPostProcessor asPlainMember;
    long liveEvent;     // starts in 30 min: check-in is open
    long laterEvent;    // starts tomorrow: check-in not open yet

    @BeforeAll
    void setUp() throws Exception {
        UUID adminId = newUserId(users);
        UUID memberId = newUserId(users);
        UUID clubId = provisioningService.provision(SLUG, "Door Club", adminId).getId();
        memberships.save(new Membership(memberId, clubId, ClubRole.MEMBER));
        asAdmin = inClub(adminId, clubId, SLUG);
        asPlainMember = inClub(memberId, clubId, SLUG);
        liveEvent = publishedEvent(Duration.ofMinutes(30));
        laterEvent = publishedEvent(Duration.ofDays(1));
    }

    long publishedEvent(Duration startsIn) throws Exception {
        Instant start = Instant.now().plus(startsIn);
        String body = mvc.perform(post("/api/club/events").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Door test","venue":"Main hall","startsAt":"%s","endsAt":"%s",
                                 "visibility":"PUBLIC"}""".formatted(start, start.plus(Duration.ofHours(2)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(post("/api/club/events/" + id + "/publish").with(asAdmin)).andExpect(status().isOk());
        return id;
    }

    /** Registers a fresh student for the event and returns their ticket. */
    String registeredTicket(UUID student, long eventId) throws Exception {
        mvc.perform(post(PUBLIC_EVENTS + eventId + "/registration").with(asUser(student)))
                .andExpect(status().isCreated());
        String json = mvc.perform(get(PUBLIC_EVENTS + eventId + "/ticket").with(asUser(student)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.ticket");
    }

    ResultActions scan(long eventId, String ticket, RequestPostProcessor as) throws Exception {
        return mvc.perform(post("/api/club/events/" + eventId + "/check-ins").with(as)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticket\":\"" + ticket + "\"}"));
    }

    @Test
    void scanningAValidTicketChecksTheStudentInOnce() throws Exception {
        UUID student = newUserId(users);
        String ticket = registeredTicket(student, liveEvent);

        scan(liveEvent, ticket, asAdmin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(student.toString()))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.method").value("QR"));
        scan(liveEvent, ticket, asAdmin).andExpect(status().isConflict());

        mvc.perform(get("/api/club/events/" + liveEvent + "/attendance").with(asAdmin))
                .andExpect(jsonPath("$.attendees[*].userId").value(org.hamcrest.Matchers.hasItem(student.toString())));
    }

    @Test
    void forgedOrMisdirectedTicketsAreRejected() throws Exception {
        UUID student = newUserId(users);
        String ticket = registeredTicket(student, liveEvent);

        // edited payload: signature no longer matches
        scan(liveEvent, ticket.replace(student.toString(), UUID.randomUUID().toString()), asAdmin)
                .andExpect(status().isBadRequest());
        // validly signed, but issued for another club's schema with the same event id
        scan(liveEvent, tickets.issue("club_some_other_club", liveEvent, student), asAdmin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This ticket belongs to another club"));
        // ticket for a different event of this club
        String laterTicket = registeredTicket(student, laterEvent);
        scan(liveEvent, laterTicket, asAdmin).andExpect(status().isBadRequest());
        // right event, but its doors aren't open yet
        scan(laterEvent, laterTicket, asAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Check-in is not open for this event"));
    }

    @Test
    void unregisteringRevokesTheTicket() throws Exception {
        UUID student = newUserId(users);
        String ticket = registeredTicket(student, liveEvent);
        // the event starts in 30 min, so unregistering is still allowed
        mvc.perform(delete(PUBLIC_EVENTS + liveEvent + "/registration").with(asUser(student)))
                .andExpect(status().isNoContent());

        scan(liveEvent, ticket, asAdmin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The ticket holder is no longer registered"));
    }

    @Test
    void coreTeamCanCheckInWalkInsByEmail() throws Exception {
        UUID walkIn = newUserId(users);
        String email = users.findById(walkIn).orElseThrow().getEmail();

        mvc.perform(post("/api/club/events/" + liveEvent + "/check-ins/manual").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("MANUAL"));
    }

    @Test
    void plainMembersCannotRunTheDoor() throws Exception {
        scan(liveEvent, "CH1.x.1.y.z", asPlainMember).andExpect(status().isForbidden());
        mvc.perform(get("/api/club/events/" + liveEvent + "/attendance").with(asPlainMember))
                .andExpect(status().isForbidden());
    }
}
