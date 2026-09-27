package com.clubhub.certificate;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.clubhub.support.TestAuth.asUser;
import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CertificateTest {

    private static final String SLUG = "cert_club";

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;

    RequestPostProcessor asAdmin;

    @BeforeAll
    void createClub() {
        UUID adminId = newUserId(users);
        UUID clubId = provisioningService.provision(SLUG, "Cert Club", adminId).getId();
        asAdmin = inClub(adminId, clubId, SLUG);
    }

    /** A published event whose check-in is open (starts in 30 minutes). */
    long liveEvent(String title) throws Exception {
        Instant start = Instant.now().plus(Duration.ofMinutes(30));
        String body = mvc.perform(post("/api/club/events").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","venue":"Hall","startsAt":"%s","endsAt":"%s","visibility":"PUBLIC"}"""
                                .formatted(title, start, start.plus(Duration.ofHours(2)))))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(post("/api/club/events/" + id + "/publish").with(asAdmin)).andExpect(status().isOk());
        return id;
    }

    void checkIn(long eventId, UUID userId) throws Exception {
        String email = users.findById(userId).orElseThrow().getEmail();
        mvc.perform(post("/api/club/events/" + eventId + "/check-ins/manual").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated());
    }

    UUID onlyCertificateOf(UUID student) throws Exception {
        String json = mvc.perform(get("/api/clubs/" + SLUG + "/certificates/mine").with(asUser(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$[0].id"));
    }

    @Test
    void attendeesGetOneCertificateEachAndCanDownloadIt() throws Exception {
        long eventId = liveEvent("Hack Night");
        UUID alice = newUserId(users);
        UUID bob = newUserId(users);
        checkIn(eventId, alice);
        checkIn(eventId, bob);

        String issue = "/api/club/events/" + eventId + "/certificates";
        mvc.perform(post(issue).with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issued").value(2));
        mvc.perform(post(issue).with(asAdmin)) // idempotent
                .andExpect(jsonPath("$.issued").value(0))
                .andExpect(jsonPath("$.alreadyIssued").value(2));

        UUID certId = onlyCertificateOf(alice);
        byte[] pdf = mvc.perform(get("/api/clubs/" + SLUG + "/certificates/" + certId + "/pdf").with(asUser(alice)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();

        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("Certificate of Participation", "Test User", "Hack Night", certId.toString());

        // bob cannot download alice's certificate
        mvc.perform(get("/api/clubs/" + SLUG + "/certificates/" + certId + "/pdf").with(asUser(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anyoneCanVerifyAndRevocationIsVisible() throws Exception {
        long eventId = liveEvent("Robotics Expo");
        UUID student = newUserId(users);
        checkIn(eventId, student);
        mvc.perform(post("/api/club/events/" + eventId + "/certificates").with(asAdmin)).andExpect(status().isOk());
        UUID certId = onlyCertificateOf(student);
        String verify = "/api/verify/certificates/" + SLUG + "/" + certId;

        mvc.perform(get(verify)) // no token at all
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.clubName").value("Cert Club"))
                .andExpect(jsonPath("$.recipientName").value("Test User"));

        mvc.perform(post("/api/club/certificates/" + certId + "/revoke").with(asAdmin)).andExpect(status().isOk());
        mvc.perform(get(verify)).andExpect(jsonPath("$.valid").value(false));
        mvc.perform(get("/api/clubs/" + SLUG + "/certificates/" + certId + "/pdf").with(asUser(student)))
                .andExpect(status().isConflict());
    }

    @Test
    void verificationOfUnknownIdsIs404() throws Exception {
        mvc.perform(get("/api/verify/certificates/" + SLUG + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/verify/certificates/no_such_club/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void cannotIssueBeforeAnyoneAttended() throws Exception {
        long eventId = liveEvent("Empty room");
        mvc.perform(post("/api/club/events/" + eventId + "/certificates").with(asAdmin))
                .andExpect(status().isConflict());
    }
}
