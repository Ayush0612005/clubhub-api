package com.clubhub.auth;

import com.clubhub.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole token lifecycle over HTTP, with real tokens and the real security filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshFlowTest {

    @Autowired MockMvc mvc;

    @Test
    void refreshRotatesTokensAndTheNewAccessTokenWorks() throws Exception {
        String login = loginNewUser();
        String oldRefresh = JsonPath.read(login, "$.refreshToken");

        String refreshed = refresh(oldRefresh)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(refreshed, "$.refreshToken")).isNotEqualTo(oldRefresh);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + JsonPath.read(refreshed, "$.accessToken")))
                .andExpect(status().isOk());
    }

    @Test
    void replayedRefreshTokenKillsTheSession() throws Exception {
        String stolen = JsonPath.read(loginNewUser(), "$.refreshToken");
        String current = JsonPath.read(refresh(stolen).andReturn().getResponse().getContentAsString(), "$.refreshToken");

        refresh(stolen).andExpect(status().isUnauthorized());      // replay detected
        refresh(current).andExpect(status().isUnauthorized());     // whole family revoked
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String refreshToken = JsonPath.read(loginNewUser(), "$.refreshToken");

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageRefreshTokenIs401() throws Exception {
        refresh("definitely-not-a-real-token").andExpect(status().isUnauthorized());
    }

    private String loginNewUser() throws Exception {
        String email = "refresh." + UUID.randomUUID() + "@srmist.edu.in";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"refresh-pass-1","fullName":"Refresh Flow"}""".formatted(email)))
                .andExpect(status().isCreated());
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"refresh-pass-1"}""".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }
}
