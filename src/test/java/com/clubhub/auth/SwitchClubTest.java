package com.clubhub.auth;

import com.clubhub.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real registration, login, club creation and switch-club over HTTP with real signed tokens. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SwitchClubTest {

    private static final String CLUB = "switch_club";

    @Autowired MockMvc mvc;
    @Autowired JwtDecoder jwtDecoder;

    String ownerLogin;     // full login response of the club's creator
    String outsiderLogin;  // full login response of a user with no membership

    @BeforeAll
    void setUp() throws Exception {
        ownerLogin = registerAndLogin();
        outsiderLogin = registerAndLogin();
        mvc.perform(post("/api/platform/tenants").header("Authorization", bearer(ownerLogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"" + CLUB + "\",\"name\":\"Switch Club\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void memberGetsAClubScopedToken() throws Exception {
        String body = switchClub(ownerLogin, CLUB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.club.slug").value(CLUB))
                .andExpect(jsonPath("$.club.role").value("CLUB_ADMIN"))
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
        assertThat(jwt.getClaimAsString("tid")).isNotBlank();
        assertThat(jwt.getClaimAsString("club")).isEqualTo(CLUB);
        assertThat(jwt.getClaimAsString("club_role")).isEqualTo("CLUB_ADMIN");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + JsonPath.read(body, "$.accessToken")))
                .andExpect(jsonPath("$.club").value(CLUB))
                .andExpect(jsonPath("$.clubRole").value("CLUB_ADMIN"));
    }

    @Test
    void onlyAClubScopedTokenOpensClubEndpoints() throws Exception {
        // plain login token: authenticated, but no active club
        mvc.perform(get("/api/club/profile").header("Authorization", bearer(ownerLogin)))
                .andExpect(status().isForbidden());

        String switched = switchClub(ownerLogin, CLUB).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/club/profile").header("Authorization", bearer(switched)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Switch Club"));
    }

    @Test
    void nonMemberAndUnknownClubGetTheSame403() throws Exception {
        String notMember = switchClub(outsiderLogin, CLUB).andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        String noSuchClub = switchClub(outsiderLogin, "no_such_club_xyz").andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        // identical responses: outsiders cannot discover which club slugs exist
        assertThat(JsonPath.<String>read(notMember, "$.detail")).isEqualTo(JsonPath.read(noSuchClub, "$.detail"));
    }

    @Test
    void switchClubRequiresAnAccessToken() throws Exception {
        mvc.perform(post("/api/auth/switch-club").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clubSlug\":\"" + CLUB + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshCanKeepTheActiveClubAndRechecksMembership() throws Exception {
        String ownerLogin2 = login(JsonPath.read(ownerLogin, "$.email"));
        refresh(JsonPath.read(ownerLogin2, "$.refreshToken"), CLUB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.club.role").value("CLUB_ADMIN"));

        // outsider asks for the club on refresh: still gets a valid token pair, just not club-scoped
        refresh(JsonPath.read(outsiderLogin, "$.refreshToken"), CLUB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.club").doesNotExist());
    }

    // ---------- helpers ----------

    private String registerAndLogin() throws Exception {
        String email = "switch." + UUID.randomUUID() + "@srmist.edu.in";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"switch-pass-1","fullName":"Switch Test"}""".formatted(email)))
                .andExpect(status().isCreated());
        String login = login(email);
        // keep the email next to the tokens so tests can log in again
        return login.substring(0, login.length() - 1) + ",\"email\":\"" + email + "\"}";
    }

    private String login(String email) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"switch-pass-1"}""".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions switchClub(String loginResponse, String slug) throws Exception {
        return mvc.perform(post("/api/auth/switch-club").header("Authorization", bearer(loginResponse))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clubSlug\":\"" + slug + "\"}"));
    }

    private ResultActions refresh(String refreshToken, String clubSlug) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\",\"clubSlug\":\"" + clubSlug + "\"}"));
    }

    private static String bearer(String loginResponse) {
        return "Bearer " + JsonPath.read(loginResponse, "$.accessToken");
    }
}
