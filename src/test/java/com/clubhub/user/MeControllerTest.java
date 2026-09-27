package com.clubhub.user;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.membership.ClubRole;
import com.clubhub.membership.Membership;
import com.clubhub.membership.MembershipRepository;
import com.clubhub.tenant.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.clubhub.support.TestAuth.asUser;
import static com.clubhub.support.TestAuth.newUserId;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MeControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired MembershipRepository memberships;
    @Autowired TenantProvisioningService provisioningService;

    @Test
    void listsMyClubsWithMyRoleInEach() throws Exception {
        UUID me = newUserId(users);
        UUID owner = newUserId(users);
        provisioningService.provision("me_alpha", "Alpha Society", me);
        UUID beta = provisioningService.provision("me_beta", "Beta Guild", owner).getId();
        memberships.save(new Membership(me, beta, ClubRole.CORE));
        provisioningService.provision("me_gamma", "Gamma Club", owner); // not mine

        mvc.perform(get("/api/me/clubs").with(asUser(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("me_alpha"))
                .andExpect(jsonPath("$[0].role").value("CLUB_ADMIN"))
                .andExpect(jsonPath("$[1].role").value("CORE"));

        mvc.perform(get("/api/me").with(asUser(me)))
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.platformAdmin").value(false));

        mvc.perform(get("/api/clubs").with(asUser(me)))
                .andExpect(jsonPath("$[*].slug", hasItem("me_gamma")));
    }
}
