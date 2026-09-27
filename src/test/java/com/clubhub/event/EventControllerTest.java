package com.clubhub.event;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.common.ConflictException;
import com.clubhub.membership.ClubRole;
import com.clubhub.membership.Membership;
import com.clubhub.membership.MembershipRepository;
import com.clubhub.tenancy.TenantContext;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.clubhub.support.TestAuth.asUser;
import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventControllerTest {

    private static final String SLUG = "event_club";
    private static final String CLUB_EVENTS = "/api/club/events";
    private static final String PUBLIC_EVENTS = "/api/clubs/" + SLUG + "/events";

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;
    @Autowired MembershipRepository memberships;
    @Autowired EventService eventService;

    String schema;
    RequestPostProcessor asAdmin;
    RequestPostProcessor asMember;

    @BeforeAll
    void createClub() {
        UUID adminId = newUserId(users);
        UUID memberId = newUserId(users);
        var club = provisioningService.provision(SLUG, "Event Club", adminId);
        schema = club.getSchemaName();
        memberships.save(new Membership(memberId, club.getId(), ClubRole.MEMBER));
        asAdmin = inClub(adminId, club.getId(), SLUG);
        asMember = inClub(memberId, club.getId(), SLUG);
    }

    /** Creates (and optionally publishes) an event starting tomorrow; returns its id. */
    long event(String title, String visibility, Integer capacity, boolean publish) throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        String body = mvc.perform(post(CLUB_EVENTS).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","venue":"TP Ganesan Auditorium","startsAt":"%s","endsAt":"%s",
                                 "capacity":%s,"visibility":"%s"}"""
                                .formatted(title, start, start.plus(2, ChronoUnit.HOURS), capacity, visibility)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        if (publish) {
            mvc.perform(post(CLUB_EVENTS + "/" + id + "/publish").with(asAdmin)).andExpect(status().isOk());
        }
        return id;
    }

    @Test
    void studentRegistersForAPublicEvent() throws Exception {
        long id = event("Open workshop", "PUBLIC", null, true);
        UUID student = newUserId(users);

        mvc.perform(get(PUBLIC_EVENTS).with(asUser(student)))
                .andExpect(jsonPath("$[*].title", hasItem("Open workshop")));
        mvc.perform(post(PUBLIC_EVENTS + "/" + id + "/registration").with(asUser(student)))
                .andExpect(status().isCreated());
        mvc.perform(post(PUBLIC_EVENTS + "/" + id + "/registration").with(asUser(student)))
                .andExpect(status().isConflict());
        mvc.perform(get(PUBLIC_EVENTS + "/" + id).with(asUser(student)))
                .andExpect(jsonPath("$.registeredCount").value(1));

        mvc.perform(get(CLUB_EVENTS + "/" + id + "/registrations").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(student.toString()))
                .andExpect(jsonPath("$[0].attended").value(false));

        mvc.perform(delete(PUBLIC_EVENTS + "/" + id + "/registration").with(asUser(student)))
                .andExpect(status().isNoContent());
        mvc.perform(get(PUBLIC_EVENTS + "/" + id).with(asUser(student)))
                .andExpect(jsonPath("$.registeredCount").value(0));
    }

    @Test
    void membersOnlyEventsAreInvisibleToOutsiders() throws Exception {
        long id = event("Members meetup", "MEMBERS", null, true);
        UUID outsider = newUserId(users);

        mvc.perform(get(PUBLIC_EVENTS).with(asUser(outsider)))
                .andExpect(jsonPath("$[*].title", not(hasItem("Members meetup"))));
        mvc.perform(post(PUBLIC_EVENTS + "/" + id + "/registration").with(asUser(outsider)))
                .andExpect(status().isNotFound());
        mvc.perform(post(CLUB_EVENTS + "/" + id + "/registration").with(asMember))
                .andExpect(status().isCreated());
    }

    @Test
    void draftsAreOnlyVisibleToTheCoreTeam() throws Exception {
        long id = event("Draft event", "PUBLIC", null, false);

        mvc.perform(get(CLUB_EVENTS + "/" + id).with(asMember)).andExpect(status().isNotFound());
        mvc.perform(get(PUBLIC_EVENTS + "/" + id).with(asUser(newUserId(users)))).andExpect(status().isNotFound());
        mvc.perform(get(CLUB_EVENTS).with(asAdmin)).andExpect(jsonPath("$[*].title", hasItem("Draft event")));
    }

    @Test
    void fullAndCancelledEventsRejectRegistrations() throws Exception {
        long small = event("Tiny room", "PUBLIC", 1, true);
        mvc.perform(post(PUBLIC_EVENTS + "/" + small + "/registration").with(asUser(newUserId(users))))
                .andExpect(status().isCreated());
        mvc.perform(post(PUBLIC_EVENTS + "/" + small + "/registration").with(asUser(newUserId(users))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This event is full"));

        long cancelled = event("Rained out", "PUBLIC", null, true);
        mvc.perform(post(CLUB_EVENTS + "/" + cancelled + "/cancel").with(asAdmin)).andExpect(status().isOk());
        mvc.perform(post(PUBLIC_EVENTS + "/" + cancelled + "/registration").with(asUser(newUserId(users))))
                .andExpect(status().isConflict());
    }

    @Test
    void capacityHoldsUnderConcurrentRegistrations() throws Exception {
        long id = event("Hot ticket", "PUBLIC", 3, true);
        List<UUID> students = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            students.add(newUserId(users));
        }

        List<Callable<Boolean>> attempts = students.stream().<Callable<Boolean>>map(s -> () ->
                TenantContext.supplyAs(schema, () -> {
                    try {
                        eventService.register(id, s, false);
                        return true;
                    } catch (ConflictException full) {
                        return false;
                    }
                })).toList();
        int succeeded = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(6)) {
            for (Future<Boolean> f : pool.invokeAll(attempts)) {
                if (f.get()) {
                    succeeded++;
                }
            }
        }

        assertThat(succeeded).isEqualTo(3);
        mvc.perform(get(CLUB_EVENTS + "/" + id).with(asAdmin)).andExpect(jsonPath("$.registeredCount").value(3));
    }

    @Test
    void membersCannotCreateEventsAndBadInputIs400() throws Exception {
        mvc.perform(post(CLUB_EVENTS).with(asMember)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","venue":"y","startsAt":"2099-01-01T10:00:00Z",
                                 "endsAt":"2099-01-01T12:00:00Z","visibility":"PUBLIC"}"""))
                .andExpect(status().isForbidden());
        mvc.perform(post(CLUB_EVENTS).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Backwards","venue":"y","startsAt":"2099-01-01T12:00:00Z",
                                 "endsAt":"2099-01-01T10:00:00Z","visibility":"PUBLIC"}"""))
                .andExpect(status().isBadRequest());
    }
}
