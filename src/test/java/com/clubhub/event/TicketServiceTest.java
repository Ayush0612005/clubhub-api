package com.clubhub.event;

import com.clubhub.event.TicketService.InvalidTicketException;
import com.clubhub.event.TicketService.Ticket;
import com.clubhub.security.JwtProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit test: no Spring context, no database. */
class TicketServiceTest {

    private static TicketService service(String secret) {
        return new TicketService(new JwtProperties(secret, "test", Duration.ofMinutes(15), Duration.ofDays(14)));
    }

    private final TicketService tickets = service("unit-test-secret-0123456789-abcdefghij");
    private final UUID user = UUID.randomUUID();

    @Test
    void roundTrips() {
        String token = tickets.issue("club_robotics", 42, user);

        assertThat(tickets.verify(token)).isEqualTo(new Ticket("club_robotics", 42, user));
    }

    @Test
    void rejectsATicketWhosePayloadWasEdited() {
        String token = tickets.issue("club_robotics", 42, user);
        String otherEvent = token.replace(".42.", ".43.");
        String otherClub = token.replace("club_robotics", "club_coding");

        assertThatThrownBy(() -> tickets.verify(otherEvent)).isInstanceOf(InvalidTicketException.class);
        assertThatThrownBy(() -> tickets.verify(otherClub)).isInstanceOf(InvalidTicketException.class);
    }

    @Test
    void rejectsATicketSignedWithAnotherKey() {
        String foreign = service("some-other-secret-0123456789-abcdefghij").issue("club_robotics", 42, user);

        assertThatThrownBy(() -> tickets.verify(foreign)).isInstanceOf(InvalidTicketException.class);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> tickets.verify("hello")).isInstanceOf(InvalidTicketException.class);
        assertThatThrownBy(() -> tickets.verify(null)).isInstanceOf(InvalidTicketException.class);
        assertThatThrownBy(() -> tickets.verify("CH1.a.1." + user + ".!!!")).isInstanceOf(InvalidTicketException.class);
    }
}
