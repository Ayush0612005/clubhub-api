package com.clubhub.file;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.clubhub.support.TestAuth.asUser;
import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pre-signing runs for real (it's local crypto with dummy credentials); only the one call that
 * would reach S3 over the network, HeadObject, is mocked.
 */
@SpringBootTest(properties = {
        "clubhub.storage.access-key=test-access-key",
        "clubhub.storage.secret-key=test-secret-key",
        "clubhub.storage.bucket=clubhub-test"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileUploadTest {

    private static final String SLUG = "file_club";
    private static final int SIZE = 204_800;

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;
    @Autowired MembershipRepository memberships;
    @MockitoBean S3Client s3;

    RequestPostProcessor asAdmin;
    RequestPostProcessor asMember;

    @BeforeAll
    void createClub() {
        UUID adminId = newUserId(users);
        UUID memberId = newUserId(users);
        UUID clubId = provisioningService.provision(SLUG, "File Club", adminId).getId();
        memberships.save(new Membership(memberId, clubId, ClubRole.MEMBER));
        asAdmin = inClub(adminId, clubId, SLUG);
        asMember = inClub(memberId, clubId, SLUG);
    }

    long publishedEvent() throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        String body = mvc.perform(post("/api/club/events").with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Poster event","venue":"Hall","startsAt":"%s","endsAt":"%s","visibility":"PUBLIC"}"""
                                .formatted(start, start.plus(1, ChronoUnit.HOURS))))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(post("/api/club/events/" + id + "/publish").with(asAdmin)).andExpect(status().isOk());
        return id;
    }

    ResultActions requestUpload(RequestPostProcessor as, Long eventId, String contentType, long size) throws Exception {
        return mvc.perform(post("/api/club/files/uploads").with(as)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"purpose":"EVENT_POSTER","eventId":%s,"contentType":"%s","sizeBytes":%d}"""
                        .formatted(eventId, contentType, size)));
    }

    String fileIdFrom(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.fileId");
    }

    void s3Holds(long size, String contentType) {
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(size).contentType(contentType).build());
    }

    @Test
    void uploadConfirmAndDownloadAPoster() throws Exception {
        long eventId = publishedEvent();

        String fileId = fileIdFrom(requestUpload(asAdmin, eventId, "image/png", SIZE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.requiredHeaders.Content-Type").value("image/png"))
                .andExpect(jsonPath("$.uploadUrl", allOf(
                        startsWith("https://clubhub-test.s3.ap-south-1.amazonaws.com/clubs/club_file_club/event_poster/"),
                        containsString("X-Amz-Signature="),
                        containsString("X-Amz-Expires=600"),
                        containsString("content-type")))));   // content type is a signed header

        s3Holds(SIZE, "image/png");
        mvc.perform(post("/api/club/files/" + fileId + "/confirm").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        // any logged-in student sees the public event's poster via a redirect to a short-lived S3 link
        mvc.perform(get("/api/clubs/" + SLUG + "/events/" + eventId + "/poster").with(asUser(newUserId(users))))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", allOf(
                        containsString("/clubs/club_file_club/event_poster/" + fileId + ".png"),
                        containsString("X-Amz-Expires=300"))));
    }

    @Test
    void confirmChecksWhatReallyLandedInS3() throws Exception {
        long eventId = publishedEvent();
        String fileId = fileIdFrom(requestUpload(asAdmin, eventId, "image/jpeg", SIZE));

        when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());
        mvc.perform(post("/api/club/files/" + fileId + "/confirm").with(asAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The file has not been uploaded yet"));

        s3Holds(SIZE * 10L, "image/jpeg"); // bigger than declared
        mvc.perform(post("/api/club/files/" + fileId + "/confirm").with(asAdmin))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/club/events/" + eventId + "/poster").with(asMember))
                .andExpect(status().isNotFound()); // never confirmed, so never attached
    }

    @Test
    void rejectsWrongTypeTooLargeAndMissingEvent() throws Exception {
        long eventId = publishedEvent();
        requestUpload(asAdmin, eventId, "application/pdf", SIZE).andExpect(status().isBadRequest());
        requestUpload(asAdmin, eventId, "image/png", 50L * 1024 * 1024).andExpect(status().isBadRequest());
        requestUpload(asAdmin, null, "image/png", SIZE).andExpect(status().isBadRequest());
        requestUpload(asAdmin, 999_999L, "image/png", SIZE).andExpect(status().isNotFound());
    }

    @Test
    void plainMembersCannotUpload() throws Exception {
        requestUpload(asMember, publishedEvent(), "image/png", SIZE).andExpect(status().isForbidden());
    }
}
