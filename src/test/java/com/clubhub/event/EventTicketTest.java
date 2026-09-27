package com.clubhub.event;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.user.UserRepository;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
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

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.clubhub.support.TestAuth.asUser;
import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventTicketTest {

    private static final String SLUG = "ticket_club";
    private static final String PUBLIC_EVENTS = "/api/clubs/" + SLUG + "/events";

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;
    @Autowired TicketService tickets;

    RequestPostProcessor asAdmin;
    long eventId;

    @BeforeAll
    void createPublishedEvent() throws Exception {
        UUID adminId = newUserId(users);
        UUID clubId = provisioningService.provision(SLUG, "Ticket Club", adminId).getId();
        asAdmin = inClub(adminId, clubId, SLUG);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        String body = mvc.perform(post("/api/club/events").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ticketed talk","venue":"UB 1","startsAt":"%s","endsAt":"%s",
                                 "visibility":"PUBLIC"}""".formatted(start, start.plus(1, ChronoUnit.HOURS))))
                .andReturn().getResponse().getContentAsString();
        eventId = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(post("/api/club/events/" + eventId + "/publish").with(asAdmin)).andExpect(status().isOk());
    }

    @Test
    void registeredStudentGetsASignedTicketAndAScannableQr() throws Exception {
        UUID student = newUserId(users);
        mvc.perform(post(PUBLIC_EVENTS + "/" + eventId + "/registration").with(asUser(student)))
                .andExpect(status().isCreated());

        String json = mvc.perform(get(PUBLIC_EVENTS + "/" + eventId + "/ticket").with(asUser(student)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ticket = JsonPath.read(json, "$.ticket");
        assertThat(tickets.verify(ticket)).isEqualTo(new TicketService.Ticket("club_" + SLUG, eventId, student));

        byte[] png = mvc.perform(get(PUBLIC_EVENTS + "/" + eventId + "/ticket/qr").with(asUser(student)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        // decode the PNG like a door scanner would: it must contain exactly the ticket
        var image = ImageIO.read(new ByteArrayInputStream(png));
        String decoded = new QRCodeReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
        assertThat(decoded).isEqualTo(ticket);
    }

    @Test
    void noTicketWithoutRegistration() throws Exception {
        mvc.perform(get(PUBLIC_EVENTS + "/" + eventId + "/ticket").with(asUser(newUserId(users))))
                .andExpect(status().isNotFound());
    }
}
