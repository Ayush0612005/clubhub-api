package com.clubhub.audit;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.membership.ClubRole;
import com.clubhub.membership.Membership;
import com.clubhub.membership.MembershipRepository;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.user.UserRepository;
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

import java.util.UUID;

import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditLogTest {

    private static final String SLUG = "audit_club";

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;
    @Autowired MembershipRepository memberships;

    UUID adminId;
    UUID coreId;
    RequestPostProcessor asAdmin;
    RequestPostProcessor asCore;

    @BeforeAll
    void createClub() {
        adminId = newUserId(users);
        coreId = newUserId(users);
        UUID clubId = provisioningService.provision(SLUG, "Audit Club", adminId).getId();
        memberships.save(new Membership(coreId, clubId, ClubRole.CORE));
        asAdmin = inClub(adminId, clubId, SLUG);
        asCore = inClub(coreId, clubId, SLUG);
    }

    @Test
    void changesAreRecordedWithTheActor() throws Exception {
        mvc.perform(post("/api/club/recruitment/drives").with(asCore)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Audited drive","questions":[{"prompt":"Why?","required":true}]}"""))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/club/audit").param("action", "DRIVE_CREATED").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorId").value(coreId.toString()))
                .andExpect(jsonPath("$.content[0].targetType").value("DRIVE"))
                .andExpect(jsonPath("$.content[0].details.title").value("Audited drive"));
    }

    @Test
    void aRejectedChangeLeavesNoAuditEntry() throws Exception {
        // demoting the only CLUB_ADMIN fails with 409, so no MEMBER_ROLE_CHANGED row may exist
        mvc.perform(patch("/api/club/members/" + adminId).with(asAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/club/audit").param("action", "MEMBER_ROLE_CHANGED").with(asAdmin))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void onlyClubAdminsCanReadTheAuditLog() throws Exception {
        mvc.perform(get("/api/club/audit").with(asCore)).andExpect(status().isForbidden());
    }
}
