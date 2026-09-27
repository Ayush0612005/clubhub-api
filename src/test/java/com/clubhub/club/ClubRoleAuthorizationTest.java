package com.clubhub.club;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.membership.ClubRole;
import com.clubhub.membership.Membership;
import com.clubhub.membership.MembershipRepository;
import com.clubhub.security.JwtTokenService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClubRoleAuthorizationTest {

    private static final String SLUG = "rbac_club";

    @Autowired MockMvc mvc;
    @Autowired TenantProvisioningService provisioning;
    @Autowired MembershipRepository memberships;
    @Autowired UserRepository users;

    UUID clubId;
    UUID coreId;
    UUID memberId;

    @BeforeAll
    void setUpClubWithThreeRoles() {
        UUID adminId = newUserId(users);
        clubId = provisioning.provision(SLUG, "RBAC Club", adminId).getId();
        coreId = newUserId(users);
        memberId = newUserId(users);
        memberships.save(new Membership(coreId, clubId, ClubRole.CORE));
        memberships.save(new Membership(memberId, clubId, ClubRole.MEMBER));
    }

    @Test
    void memberCanReadButNotEditTheProfile() throws Exception {
        mvc.perform(get("/api/club/profile").with(inClub(memberId, clubId, SLUG)))
                .andExpect(status().isOk());

        updateProfile(inClub(memberId, clubId, SLUG), "Member Was Here")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Your role does not allow this action"));
    }

    @Test
    void coreCanEditTheProfile() throws Exception {
        updateProfile(inClub(coreId, clubId, SLUG), "Edited By Core")
                .andExpect(status().isOk());
    }

    @Test
    void roleComesFromTheDatabaseNotFromTheToken() throws Exception {
        UUID demotedId = newUserId(users);
        memberships.save(new Membership(demotedId, clubId, ClubRole.MEMBER));

        // token still claims CLUB_ADMIN (e.g. issued before a demotion): must not matter
        RequestPostProcessor staleAdminToken = jwt()
                .jwt(j -> j.subject(demotedId.toString())
                        .claim(JwtTokenService.CLAIM_ROLES, List.of("USER"))
                        .claim(JwtTokenService.CLAIM_TENANT_ID, clubId.toString())
                        .claim(JwtTokenService.CLAIM_CLUB, SLUG)
                        .claim(JwtTokenService.CLAIM_CLUB_ROLE, "CLUB_ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));

        updateProfile(staleAdminToken, "Should Not Stick").andExpect(status().isForbidden());
    }

    @Test
    void promotionTakesEffectImmediately() throws Exception {
        UUID risingStar = newUserId(users);
        Membership membership = memberships.save(new Membership(risingStar, clubId, ClubRole.MEMBER));
        updateProfile(inClub(risingStar, clubId, SLUG), "Too Early").andExpect(status().isForbidden());

        membership.changeRole(ClubRole.CORE);
        memberships.save(membership);

        // same token, no re-login: the next request already sees the new role
        updateProfile(inClub(risingStar, clubId, SLUG), "Promoted").andExpect(status().isOk());
    }

    private ResultActions updateProfile(RequestPostProcessor auth, String displayName) throws Exception {
        return mvc.perform(put("/api/club/profile").with(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"" + displayName + "\"}"));
    }
}
