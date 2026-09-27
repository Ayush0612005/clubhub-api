package com.clubhub.event;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.tenancy.TenantContext;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.user.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

import static com.clubhub.support.TestAuth.newUserId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventRepositoryTest {

    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired EventRegistrationRepository registrations;
    @Autowired AttendanceRepository attendance;
    @Autowired TransactionTemplate tx;

    String schema;
    UUID ownerId;

    @BeforeAll
    void createClub() {
        ownerId = newUserId(users);
        schema = provisioningService.provision("event_repo_club", "Event Repo Club", ownerId).getSchemaName();
    }

    <T> T inClub(Supplier<T> block) {
        return TenantContext.supplyAs(schema, () -> tx.execute(status -> block.get()));
    }

    Event event(Instant startsAt) {
        return new Event("Hack night", null, "TP 401", startsAt, startsAt.plus(3, ChronoUnit.HOURS),
                50, EventVisibility.PUBLIC, ownerId);
    }

    @Test
    void savesAndListsUpcomingEvents() {
        Instant now = Instant.now();
        Long upcoming = inClub(() -> events.save(event(now.plus(2, ChronoUnit.DAYS))).getId());
        Long past = inClub(() -> events.save(event(now.minus(5, ChronoUnit.DAYS))).getId());

        var ids = inClub(() -> events.findAllByEndsAtAfterOrderByStartsAtAsc(now).stream().map(Event::getId).toList());

        assertThat(ids).contains(upcoming).doesNotContain(past);
    }

    @Test
    void lockedLookupWorksInsideATransaction() {
        Long id = inClub(() -> events.save(event(Instant.now().plus(1, ChronoUnit.DAYS))).getId());
        assertThat(inClub(() -> events.findForUpdateById(id))).isPresent();
    }

    @Test
    void registrationAndAttendanceAreUniquePerUser() {
        Long id = inClub(() -> events.save(event(Instant.now().plus(1, ChronoUnit.DAYS))).getId());
        UUID student = newUserId(users);

        inClub(() -> registrations.save(new EventRegistration(id, student)));
        inClub(() -> attendance.save(new Attendance(id, student, AttendanceMethod.QR, ownerId)));
        assertThat(inClub(() -> registrations.countByEventId(id))).isEqualTo(1);

        assertThatThrownBy(() -> inClub(() -> registrations.saveAndFlush(new EventRegistration(id, student))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inClub(() ->
                attendance.saveAndFlush(new Attendance(id, student, AttendanceMethod.MANUAL, ownerId))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void eventRulesDependOnStatusAndTime() {
        Instant start = Instant.now().plus(3, ChronoUnit.HOURS);
        Event e = event(start);
        assertThat(e.isOpenForRegistration(Instant.now())).isFalse(); // draft
        assertThat(e.isVisibleTo(true)).isFalse();

        e.publish();
        assertThat(e.isOpenForRegistration(Instant.now())).isTrue();
        assertThat(e.isOpenForRegistration(start)).isFalse();                         // started
        assertThat(e.isCheckInOpen(start.minus(2, ChronoUnit.HOURS))).isFalse();      // too early
        assertThat(e.isCheckInOpen(start.minus(30, ChronoUnit.MINUTES))).isTrue();    // doors open
        assertThat(e.isCheckInOpen(start.plus(4, ChronoUnit.HOURS))).isFalse();       // over

        assertThatThrownBy(e::publish).isInstanceOf(IllegalStateException.class);
        e.cancel();
        assertThat(e.isCheckInOpen(start)).isFalse();
    }

    @Test
    void rejectsEventEndingBeforeItStarts() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new Event("x", null, "v", now, now, null, EventVisibility.PUBLIC, ownerId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
